# РЕАЛИЗАЦИЯ ОГИБАНИЯ ТРОСОВ - ПОЛНОЕ РУКОВОДСТВО

**Основано на:** Grappling Hook Mod (yyon/grapplemod) SegmentHandler.java

---

## 🎯 АРХИТЕКТУРА РЕШЕНИЯ

### ИЗ GRAPPLING HOOK МОДА (yyon):

```java
// Ключевые компоненты:
LinkedList<Vec> segments;           // Точки изгибов (bends) где трос огибает блоки
LinkedList<Direction> segmentBottomSides;  // Направления граней блоков (нижняя грань)
LinkedList<Direction> segmentTopSides;     // Направления граней блоков (верхняя грань)
```

### АЛГОРИТМ:

```
1. updateSegment() - проверяет линию между игроком и крюком на столкновения с блоками
2. Если трос проходит сквозь блок:
   a. Вычисляет угол блока (corner)
   b. Вычисляет точку изгиба (bend point)
   c. Добавляет новый сегмент в список (actuallyAddSegment)
3. Удаляет сегменты которые больше не нужны (removeSegment)
4. Рекурсивно проверяет оставшиеся части троса
```

### КЛЮЧЕВЫЕ МЕТОДЫ ДЛЯ АДАПТАЦИИ:

```java
// 1. RAYCAST - проверка пересечения линии с блоком
GrapplemodUtils.rayTraceBlocks(world, point1, point2)  
// АНАЛОГ В DAOT: ClipContext + world.clip()

// 2. ЛИНИЯ-ПЛОСКОСТЬ ПЕРЕСЕЧЕНИЕ
linePlaneIntersection(Vec linePoint1, Vec linePoint2, Vec planePoint, Vec planeNormal)
// МАТЕМАТИКА: использует формулу с вики:
// https://en.wikipedia.org/wiki/Line%E2%80%93plane_intersection#Algebraic_form

// 3. ВЫЧИСЛЕНИЕ ТОЧКИ ИЗГИБА
bend = actualCorner.add(bottomNormal.changeLen(intoBlock))
       .add(getNormal(cornerSide).changeLen(bendOffset));
// intoBlock = 0.05 (смещение внутрь блока)
// bendOffset = 0.05 (смещение от края)

// 4. РЕКУРСИЯ - проверка верхней части троса
updateSegment(top, prevTop, bend, prevBend, index, numberRecursions+1);
```

---

## 📋 ПЛАН РЕАЛИЗАЦИИ ДЛЯ DAOT-COMPAT

### ШАГ 1: Создать класс RopeSegmentHandler (адаптация)

```java
// src/main/java/com/armorberserk/daotcompat/physics/RopeSegmentHandler.java

@OnlyIn(Dist.CLIENT)
public class RopeSegmentHandler {
    private LinkedList<Vec3> segments;
    private LinkedList<Direction> bottomSides;
    private LinkedList<Direction> topSides;
    
    private static final double BEND_OFFSET = 0.05;
    private static final double INTO_BLOCK = 0.05;
    
    public RopeSegmentHandler(Vec3 hookPos, Vec3 playerPos) {
        segments = new LinkedList<>();
        segments.add(hookPos);
        segments.add(playerPos);
        bottomSides = new LinkedList<>();
        topSides = new LinkedList<>();
        // ... инициализация
    }
    
    public void update(Vec3 hookPos, Vec3 playerPos, double ropeLen, Level level) {
        // Адаптировать updateSegment() из гrappling hook
        // 1. Рейкаст между playerPos и hookPos
        // 2. Если есть блок - вычислить bend
        // 3. Добавить сегмент
        // 4. Рекурсивно проверить остаток троса
    }
    
    private void updateSegment(Vec3 top, Vec3 prevTop, Vec3 bottom, Vec3 prevBottom, 
                               int index, int recursions, Level level, double ropeLen) {
        // Основная логика из yyon's SegmentHandler
    }
    
    private Vec3 linePlaneIntersection(Vec3 linePoint1, Vec3 linePoint2, 
                                       Vec3 planePoint, Vec3 planeNormal) {
        // Формула: https://en.wikipedia.org/wiki/Line%E2%80%93plane_intersection#Algebraic_form
        Vec3 lineVec = linePoint2.subtract(linePoint1);
        double denominator = lineVec.dot(planeNormal);
        if (Math.abs(denominator) < 1e-6) return null;
        double t = planePoint.subtract(linePoint1).dot(planeNormal) / denominator;
        return linePoint1.add(lineVec.scale(t));
    }
}
```

### ШАГ 2: Интегрировать в GrapplePhysicsController

```java
// Вместо простого checkRopeCollision() с обрывом:
private static RopeSegmentHandler segmentHandler;

public static void tick(LocalPlayer player) {
    // ...
    if (leftHook != null || rightHook != null) {
        // Инициализировать handler
        if (segmentHandler == null) {
            segmentHandler = new RopeSegmentHandler(hookPos, player.position());
        }
        
        // Обновить с отгибаниями
        segmentHandler.update(hookPos, player.position(), currentRopeLength, 
                             player.level());
        
        // Использовать segments для физики:
        // - Рендеринг (RopeLineRenderer)
        // - Коллизии
        // - Визуальные эффекты
    }
}
```

### ШАГ 3: Использовать segments для рендера

```java
// RopeLineRenderer.java
public static void onRenderLevel(RenderLevelStageEvent event) {
    Vec3[] ropePoints = segmentHandler.getSegments();  // Использовать segments!
    
    // Отрисовать линию через все segments с огибанием
    for (int i = 0; i < ropePoints.length - 1; i++) {
        drawRopeLine(ropePoints[i], ropePoints[i+1]);  // Видно изгибы!
    }
}
```

---

## 🔧 КРИТИЧЕСКИЕ ДЕТАЛИ ДЛЯ ПЕРЕВОДА

| Понятие (yyon) | Адаптация (DAOT) | Описание |
|---|---|---|
| `Vec` (custom) | `Vec3` (Minecraft) | Вектор в пространстве |
| `GrapplemodUtils.rayTraceBlocks()` | `ClipContext + world.clip()` | Проверка столкновения |
| `Direction.getNormal()` | `Vec3` из `Direction` | Нормаль грани блока |
| `Segments[i]` | LinkedList точек | Точки где трос огибает |
| `SegmentBottomSides[i]` | LinkedList граней | На какой грани какой блок |

---

## 🎮 РЕЗУЛЬТАТ

**После реализации:**
- ✅ Трос огибает углы блоков
- ✅ Визуально видны "bends" (изгибы) в рендере
- ✅ Физика учитывает реальную длину троса по огибам
- ✅ Движение по изгибам плавное

**Сложность:** ВЫСОКАЯ (3-4 часа кодирования + тестирования)

**Зависимости:** Нет (всё в Minecraft API)

---

## 📚 ССЫЛКА НА ОРИГИНАЛ

- **Файл:** yyon/grapplemod/src/main/java/com/yyon/grapplinghook/entities/grapplehook/SegmentHandler.java
- **GitHub:** https://github.com/yyon/grapplemod
- **Лицензия:** Проверить перед адаптацией (идея + алгоритм, не код 1-в-1)

---

## 🚀 СЛЕДУЮЩИЕ ШАГИ

1. Создать RopeSegmentHandler.java
2. Интегрировать в GrapplePhysicsController
3. Обновить RopeLineRenderer для использования segments
4. Тестировать с огибанием блоков
5. Оптимизировать рекурсию (max depth = 10)

