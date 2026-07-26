# 🔀 ЭТАП 3: РАЗЛИЧИЯ VANILLA vs PHYSICS (Create: Aeronautics / Sable)

**Статус:** ✅ Разделение логики НАЙДЕНО
**Дата:** 26 июля 2026, 03:45+ UTC

---

## 🎯 ГЛАВНАЯ ТОЧКА РАЗДЕЛЕНИЯ

**Файл:** `HookTransformResolver.java`

**Методы:**
- `process()` - основной входной метод
- `attach()` - первое зацепление крюка (РАЗДЕЛЕНИЕ ЗДЕСЬ)
- `follow()` - синхронизация позиции при движении объекта
- `recoverPlotFrame()` - обработка координат Sable

---

## 📍 ГДЕ НАЧИНАЕТСЯ РАЗДЕЛЕНИЕ

### VANILLA ЛОГИКА (обычные блоки)

```java
public static void process(@Nullable Level level, @Nullable Object hook) {
    // 1. Получить позицию крюка (в world координатах)
    Vec3 world = AOTReflect.getPosition(hook);
    
    DynamicHookData anchor = DynamicHookMap.get(hook);
    if (anchor == null) {
        attach(level, hook, world);  // ← Первое зацепление
    } else {
        follow(level, hook, anchor, world);  // ← Синхронизация
    }
}

// VANILLA: attach() не найдет SubLevel
private static void attach(Level level, Object hook, Vec3 world) {
    SubLevel sl = SubLevelResolver.findContaining(level, world);
    if (sl != null) {
        // ← НЕ ВОЙДЕТ СЮДА для vanilla блоков
    }
    // Fallback: просто используем world позицию как-есть
    // (На самом деле дальше идет recoverPlotFrame, но он тоже ничего не найдет)
}
```

**Результат:** Позиция крюка = world позиция, СТАТИЧНА навсегда.

### PHYSICS ЛОГИКА (Sable / Create: Aeronautics)

```java
private static void attach(Level level, Object hook, Vec3 world) {
    // 1. КРИТИЧНО: найти Sub-level который содержит эту позицию
    SubLevel sl = SubLevelResolver.findContaining(level, world);
    
    if (sl != null) {  // ← ВХОДИТ СЮДА для physics объектов
        UUID id = sl.getUniqueId();
        
        // 2. ТРАНСФОРМАЦИЯ МАТРИЦА: преобразовать world → local координаты
        Vec3 local = sl.logicalPose().transformPositionInverse(world);
        
        // 3. СОХРАНИТЬ ЯКОРЬ: запомнить local позицию + UUID объекта
        DynamicHookMap.put(hook, new DynamicHookData(id, local, level.dimension()));
    }
}

// PHYSICS: follow() каждый тик обновляет позицию
private static void follow(Level level, Object hook, DynamicHookData anchor, Vec3 world) {
    // Получить Sable объект (sub-level) по UUID
    SubLevel sl = SableBridge.getSubLevel(level, anchor.subLevelId());
    
    // ТРАНСФОРМАЦИЯ: local → world для текущей позиции корабля
    Vec3 next = sl.logicalPose().transformPosition(anchor.localPosition());
    
    // Обновить позицию крюка в world координатах
    AOTReflect.setPosition(hook, next);  // ← НОВАЯ ПОЗИЦИЯ!
}
```

**Результат:** Позиция крюка обновляется каждый тик следуя кораблю!

---

## 🔗 СВЯЗЬ С GrapplePhysicsController

### Для VANILLA блоков:
```
HookTransformResolver.attach()
  └─ Позиция крюка = статична

GrapplePhysicsController.applyRopeConstraint()
  └─ Использует статичную позицию
  └─ Ограничение работает правильно
```

### Для PHYSICS объектов:
```
HookTransformResolver.attach()
  └─ Позиция крюка запомнена в local координатах

RemoteHookFollower.tick() КАЖДЫЙ ТИК
  └─ Вызывает HookTransformResolver.process()
  └─ HookTransformResolver.follow()
  └─ Обновляет позицию крюка в НОВЫХ world координатах

GrapplePhysicsController.applyRopeConstraint()
  └─ Использует ОБНОВЛЕННУЮ позицию
  └─ Ограничение работает правильно
```

**КРИТИЧНО:** Если HookTransformResolver.follow() не вызывается или работает неправильно, трос станет рассинхронизирован!

---

## ⚠️ ОБНАРУЖЕННЫЕ ПРОБЛЕМЫ

### Проблема 1: VANILLA блоки вообще НЕ ОБРАБАТЫВАЮТСЯ

**Файл:** `HookTransformResolver.java` строка 49-52

```java
if (!AOTReflect.isActive(hook) || AOTReflect.isOnEntity(hook)) {
    DynamicHookMap.put(hook, null);  // ← Очистить данные
    return;  // ← ВЫХОД! Ничего не делаем!
}
```

**Вопрос:** Как работают vanilla блоки если мы их НЕ обрабатываем?

**Ответ:** AOT сам управляет позицией крюка в этом случае. Мод просто не вмешивается.

---

### Проблема 2: SubLevelResolver.findContaining() может вернуть NULL

**Файл:** `HookTransformResolver.java` строка 67

```java
SubLevel sl = SubLevelResolver.findContaining(level, world);
if (sl != null) {
    // Обработать как Physics объект
} else {
    // ← ПРОБЛЕМА: ЧТО ЗДЕСЬ? 
    // Может быть vanilla блок вне sub-level?
    // Может быть ошибка позиции?
}
```

**Далее идет `recoverPlotFrame()`** - пытается восстановить plot-координаты.

---

### Проблема 3: PLOT_FRAME recovery может быть ненадежным

**Файл:** `HookTransformResolver.java` строка 90-112

```java
private static void recoverPlotFrame(Level level, Object hook, Vec3 reported) {
    // Проверка: если позиция > 1 млн блоков от игрока
    if (reported.distanceToSqr(eye) < PLOT_FRAME_SQR) return; // ordinary world hit
    
    // Поиск: проверить ВСЕ sub-levels в уровне
    for (SubLevel sl : SableBridge.getAllSubLevels(level)) {
        Vec3 visual = sl.logicalPose().transformPosition(reported);
        
        // Проверка: новая позиция должна быть в диапазоне
        if (visual.distanceToSqr(eye) > MATCH_RADIUS_SQR) continue;
    }
}
```

**Проблема:** Если есть несколько sub-levels рядом, может быть false-positive!

---

## 📊 ТАБЛИЦА РАЗЛИЧИЙ

| Аспект | VANILLA | PHYSICS |
|--------|---------|---------|
| **Обнаружение** | AOT сам управляет | HookTransformResolver.attach() |
| **Хранение позиции** | В AOT объекте | В DynamicHookData (local coords) |
| **Обновление позиции** | AOT (один раз?) | RemoteHookFollower.follow() ЕТК тик |
| **Трансформация** | Нет | Матрица Sable (transformPosition) |
| **Синхронизация** | Статична | Следует за кораблем |
| **Результат** | Крюк на блоке | Крюк на движущемся корабле |

---

## 🔍 КОНКРЕТНЫЕ МЕСТА РАЗДЕЛЕНИЯ ЛОГИКИ

### 1. **SubLevelResolver.findContaining()** (строка 67)

```java
SubLevel sl = SubLevelResolver.findContaining(level, world);
```

- Возвращает `SubLevel` если world точка внутри sub-level
- Возвращает `null` если обычный vanilla блок

**Это главная развилка!**

### 2. **AOTReflect.isOnEntity()** (строка 50)

```java
if (AOTReflect.isOnEntity(hook)) {
    DynamicHookMap.put(hook, null);  // Не обрабатываем крюки на сущностях
    return;
}
```

- Если крюк на мобе/игроке → выход
- На обычные объекты не смотрим

### 3. **transformPositionInverse()** vs **transformPosition()** (строки 73 и 130)

```java
// При зацеплении: world → local
Vec3 local = sl.logicalPose().transformPositionInverse(world);

// При синхронизации: local → world
Vec3 next = sl.logicalPose().transformPosition(anchor.localPosition());
```

- **Inverse:** конвертирует world координаты в local (для сохранения)
- **Forward:** конвертирует local координаты в world (для обновления)

---

## 🎮 КАК ЭТО ВЛИЯЕТ НА GAMEPLAY

### VANILLA блоки:
1. Игрок зацепляется за обычный блок
2. AOT сохраняет позицию блока в hook объекте
3. Трос идет к этой статичной позиции
4. Ограничение работает правильно
5. **РАБОТАЕТ ✅**

### PHYSICS объекты (без fix):
1. Игрок зацепляется за корабль (sub-level)
2. AOT сохраняет world позицию в hook объекте
3. Корабль движется вправо
4. Трос остается к старой позиции (корабль уехал)
5. **РАЗРЫВ СИНХРОНИЗАЦИИ ❌**

### PHYSICS объекты (с fix):
1. Игрок зацепляется за корабль (sub-level)
2. HookTransformResolver сохраняет local позицию + UUID корабля
3. Корабль движется вправо
4. RemoteHookFollower.follow() пересчитывает новую world позицию
5. Трос следует за кораблем
6. **РАБОТАЕТ ✅**

---

## 🚀 ПОЧЕМУ В v1.3.4 БЫЛА ПРОБЛЕМА

**Гипотеза:** RemoteHookFollower.follow() не вызывалась или не обновляла позицию правильно

### Возможные причины:

1. **Двойная обработка (ИСПРАВЛЕНО в v1.3.4)**
   - HookTransformResolver вызывалась из двух мест
   - RemoteHookFollower.follow() вызывалась из двух мест
   - Вероятно было race condition

2. **Неправильный порядок вызовов**
   - Если HookTransformResolver вызывается ПОСЛЕ GrapplePhysicsController
   - То физика применяется к старым позициям крюков

3. **Missing cache invalidation**
   - DynamicHookMap кэшируется но не очищается правильно

---

## ✅ АНАЛИЗ ЗАВЕРШЕН

**Найденное разделение логики:**
- ✅ SubLevelResolver.findContaining() = точка ветвления
- ✅ DynamicHookData = хранилище для physics позиций
- ✅ HookTransformResolver.follow() = синхронизация каждый тик
- ✅ RemoteHookFollower вызывает follow()

**Статус проблем:**
1. ⚠️ Если RemoteHookFollower.follow() не вызывается → рассинхронизация
2. ⚠️ Если порядок вызовов неправильный → старые позиции
3. ⚠️ Если COLLISION_CHECK_INTERVAL слишком большой → промахи в коллизиях

**Следующий шаг:** Составить ТАБЛИЦУ ПРОБЛЕМ (ЭТАП 4) с точными методами
