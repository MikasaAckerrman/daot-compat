# ПОЛНЫЙ КОНТЕКСТ СЕССИИ: ODM PHYSICS CODE AUDIT & BUG FIXES

**Дата начала:** 25 июля 2026  
**Дата обновления:** 26 июля 2026 01:02 UTC+4  
**Статус:** В ПРОЦЕССЕ (40% готовности)

---

## ⚠️ ПРАВИЛЬНЫЙ ИСТОЧНИК ИНСТРУКЦИЙ

### НЕПРАВИЛЬНО ❌
- Я ВНАЧАЛЕ читал случайные `*.md` файлы в репо (STAGE1_SPEC.md, STAGE2_SPEC.md и т.д.)
- Это был **НЕПРАВИЛЬНЫЙ** контекст (для Stage 2, Stage 3, а не для текущего аудита)

### ПРАВИЛЬНО ✅
**Источник инструкций - GitHub коммит:**
```
https://github.com/MikasaAckerrman/daot-compat/commit/945470494b06a2357f84c1c6e1a9e8c6828504a4
```

**Файл:** `debug/logs/insruchia.txt` (254 строк)

**Содержит:** Описание от пользователя (Insruchia) о РЕАЛЬНЫХ БАГАХ в ODM физике

---

## 📖 ЧТО НУЖНО ДЕЛАТЬ (из insruchia.txt)

### ОСНОВНЫЕ БАГИ (PRIORITY 1 - КРИТИЧЕСКИЕ):

1. **❌ Трос автоматически тянет игрока**
   - ПРОБЛЕМА: ЛКМ → крюк попал → игрок летит сам
   - ДОЛЖНО БЫТЬ: ЛКМ → крюк зацепился → натяжение ЕСТЬ но игрока НЕТ
   - ТРЕБУЕТСЯ: Space для подтягивания, Shift для ослабления
   - **ФАЙЛ:** `GrapplePhysicsController.java`

2. **❌ Нет физики натяжения**
   - ПРОБЛЕМА: Игрок летит в любую сторону, трос не мешает
   - ДОЛЖНО БЫТЬ: Ограничение по ДЛИНЕ троса (сфера вокруг крюка)
   - ФОРМУЛА: 
     ```
     если расстояние < длина троса: ничего
     если расстояние > длина троса: возврат к сфере
     ```
   - **ФАЙЛ:** `GrapplePhysicsController.java` + `DEWImpulseCalculator.java`

3. **❌ Трос проходит сквозь блоки**
   - ПРОБЛЕМА: Лазер через стены
   - ДОЛЖНО БЫТЬ: 
     - огибать угол ИЛИ
     - оборваться ИЛИ
     - перецепиться
   - **ФАЙЛ:** `GrapplePhysicsController.java` + нужен `RopeSegmentHandler.java` (новый файл!)
   - **РЕАЛИЗАЦИЯ:** Адаптировать из yyon/grapplemod SegmentHandler.java

4. **❌ Нет сохранения импульса**
   - ПРОБЛЕМА: Скорость резко меняется (40→38→39→5→32→0)
   - ДОЛЖНО БЫТЬ: Плавная инерция как в ODM аниме
   - **ФАЙЛ:** `GrapplePhysicsController.java` + `DEWImpulseCalculator.java`

5. **❌ Нет звуков при DEW**
   - ПРОБЛЕМА: Никакого audio feedback
   - ДОЛЖНО БЫТЬ: Звук `SoundEvents.BLAZE_SHOOT` при активации
   - **ФАЙЛ:** `KeybindEventListener.java`
   - **СТАТУС:** ✅ ИСПРАВЛЕНО (добавлены playSound вызовы)

### ДОПОЛНИТЕЛЬНЫЕ БАГИ (PRIORITY 2 - НЕОПТИМИЗАЦИЯ):

6. **⚠️ ReelControl.java должен быть удален**
   - Конфликтует с GrapplePhysicsController v1.2.0
   - Дублирует функциональность
   - Отмечен как "DISABLED" но всё ещё в проекте

7. **⚠️ AOTReflect.getLeftHook/getRightHook вызывается 5+ раз за тик**
   - Отражение (reflection) дорого!
   - ОПТИМИЗАЦИЯ: Кешировать в DAOTCompat.onClientTick()
   - Передавать hookObjects параметром в методы

8. **⚠️ SableRopeIntegration.java полностью TODO**
   - Все методы - placeholder
   - Нужна полная реализация через Sable RopePhysicsObject

---

## 📂 СТРУКТУРА ПРОЕКТА - КЛЮЧЕВЫЕ ФАЙЛЫ

```
/home/user/workspace/daot-compat/

src/main/java/com/armorberserk/daotcompat/

├── physics/
│   ├── GrapplePhysicsController.java          [ОСНОВНОЙ ФАЙЛ - трос физика]
│   ├── DEWImpulseCalculator.java             [Расчет импульса DEW]
│   ├── SableRopeIntegration.java             [Интеграция с Sable (TODO)]
│   └── RopeSegmentHandler.java               [🆕 НУЖНО СОЗДАТЬ - огибание]
│
├── input/
│   ├── KeybindEventListener.java             [🔊 ИСПРАВЛЕНО - добавлены звуки]
│   └── DoubleTapDetector.java                [Детектор двойного нажатия]
│
├── gas/
│   └── GasManager.java                       [Управление газом (энергией)]
│
├── render/
│   ├── RopeLineRenderer.java                 [Рендер линии троса]
│   └── SparkEffectRenderer.java              [Частицы при скольжении]
│
├── hook/
│   ├── ReelControl.java                      [❌ ДОЛЖЕН БЫТЬ УДАЛЕН]
│   ├── HookTransformResolver.java            [Трансформация крюка]
│   └── RemoteHookFollower.java               [Следование за крюком]
│
├── aot/
│   ├── AOTReflect.java                       [Reflection доступ к AOT jar]
│   ├── RemoteHookReflect.java
│   └── SpearReflect.java
│
└── DAOTCompat.java                           [ГЛАВНЫЙ КЛАСС - точка входа]

debug/logs/
└── insruchia.txt                             [✅ ПРАВИЛЬНАЯ ИНСТРУКЦИЯ]
```

---

## ✅ ЧТО МЫ СДЕЛАЛИ СЕЙЧАС

### СЕАНС 1: АУДИТ И АНАЛИЗ

1. **Прочитал ВСЕ файлы физики:**
   - GrapplePhysicsController.java (полностью)
   - DEWImpulseCalculator.java (полностью)
   - SableRopeIntegration.java (полностью)
   - RopeLineRenderer.java (полностью)
   - SparkEffectRenderer.java (полностью)
   - KeybindEventListener.java (полностью)

2. **Создал документы анализа:**
   - `/home/user/workspace/daot-compat/AUDIT_INSRUCHIA_15BUGS.md` - таблица всех 15 багов
   - `/home/user/workspace/daot-compat/ROPE_WRAPPING_IMPLEMENTATION_GUIDE.md` - руководство реализации

3. **Нашел правильный источник:**
   - Клонировал `yyon/grapplemod` (https://github.com/yyon/grapplemod)
   - Нашел `SegmentHandler.java` - ПРАВИЛЬНАЯ реализация огибания тросов
   - Описал как адаптировать (yyon Forge → DAOT NeoForge)

4. **Исправил первый баг:**
   - ✅ **БАГ #2: ЗВУКИ ПРИ DEW**
     - Добавлен `player.playSound(SoundEvents.BLAZE_SHOOT, 0.6f, pitch)` в KeybindEventListener.java (строки 47, 56)

---

## 🎯 СЛЕДУЮЩИЕ ШАГИ (НА СЛЕДУЮЩУЮ СЕССИЮ)

### PRIORITY 1 - НЕМЕДЛЕННО:

- [ ] **Создать RopeSegmentHandler.java** (адаптация из yyon/grapplemod)
  - LinkedList<Vec3> segments (точки изгибов)
  - Метод update() с raycast проверкой
  - Метод updateSegment() с рекурсией
  - Метод linePlaneIntersection() для вычисления bend point
  
- [ ] **Интегрировать RopeSegmentHandler в GrapplePhysicsController**
  - Заменить checkRopeCollision() на использование segments
  - Использовать segments для отрисовки
  
- [ ] **Удалить ReelControl.java**
  - Проверить что логика перенесена в GrapplePhysicsController
  - Обновить DAOTCompat.java (удалить вызовы)

- [ ] **Оптимизировать AOTReflect вызовы**
  - Кешировать hookObjects в DAOTCompat.onClientTick()
  - Передавать параметром, не вызывать каждый раз

### PRIORITY 2 - ПОТОМ:

- [ ] Завершить SableRopeIntegration.java
- [ ] Улучшить визуал RopeLineRenderer
- [ ] Тестировать физику натяжения
- [ ] Добавить звуки при обрыве/изгибе

---

## 🔧 ТЕХНИЧЕСКИЕ ДЕТАЛИ

### GrapplePhysicsController.java - текущая структура:

```java
public class GrapplePhysicsController {
    // Текущие методы:
    - tick(LocalPlayer)           // Главный loop
    - checkRopeCollision()         // ❌ ТОЛЬКО ОБРЫВ, БЕЗ ОГИБАНИЯ
    - apply()                      // Применение физики
    - updatePlayerVelocity()       // Обновление скорости
    
    // НУЖНО ДОБАВИТЬ:
    - RopeSegmentHandler segmentHandler
    - updateSegmentsForWrapping()
}
```

### DEWImpulseCalculator.java - проблемы:

```java
- НЕ сохраняет импульс (просто заменяет velocity)
- НЕ добавляет вращение (yRot для маятника)
- НЕ добавляет сопротивление воздуха (drag)
```

### SableRopeIntegration.java - статус:

```java
createRope()    // ❌ TODO (line 56-63 commented out)
updateRope()    // ❌ TODO
removeRope()    // ❌ PLACEHOLDER
getRopePoints() // ✅ Рабочая (но без Sable, fallback с провисанием)
```

---

## 📚 СПРАВОЧНАЯ ИНФОРМАЦИЯ

### Откуда взять нужные классы:

1. **SegmentHandler (для огибания):**
   - Источник: https://github.com/yyon/grapplemod/blob/master/src/main/java/com/yyon/grapplinghook/entities/grapplehook/SegmentHandler.java
   - Локально: `/home/user/workspace/grapplemod_ref/src/main/java/.../SegmentHandler.java`

2. **GrappleController (для вдохновения):**
   - Источник: https://github.com/yyon/grapplemod/blob/master/src/main/java/com/yyon/grapplinghook/controllers/GrappleController.java
   - 1046 строк - очень большой файл, но хорошая справка

### Ключевые алгоритмы:

- **Raycast:** `ClipContext + level.clip()` (вместо yyon's rayTraceBlocks)
- **Line-Plane intersection:** https://en.wikipedia.org/wiki/Line%E2%80%93plane_intersection#Algebraic_form
- **Bend detection:** Direction.getNormal() для получения нормалей граней блоков

---

## 🚀 КАК ПРОДОЛЖИТЬ В СЛЕДУЮЩЕЙ СЕССИИ

1. **Прочитай этот документ** - он содержит всё необходимое

2. **Не смотри другие документы** (они для других этапов):
   - STAGE1_SPEC.md ❌ (для Stage 1, уже пройден)
   - STAGE2_SPEC.md ❌ (для Stage 2, другие требования)
   - PLAN.txt ❌ (слишком общий)
   - Смотри ТОЛЬКО insruchia.txt ✅

3. **Порядок действий:**
   ```
   1. Создать RopeSegmentHandler.java (300-400 строк)
   2. Интегрировать в GrapplePhysicsController
   3. Удалить ReelControl.java
   4. Оптимизировать AOTReflect
   5. Тестировать сборку
   ```

4. **Файлы для редактирования:**
   ```
   EDIT: /src/main/java/com/armorberserk/daotcompat/physics/GrapplePhysicsController.java
   NEW:  /src/main/java/com/armorberserk/daotcompat/physics/RopeSegmentHandler.java
   DEL:  /src/main/java/com/armorberserk/daotcompat/hook/ReelControl.java
   EDIT: /src/main/java/com/armorberserk/daotcompat/DAOTCompat.java
   ```

5. **Сборка для проверки:**
   ```bash
   cd /home/user/workspace/daot-compat
   JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew clean build -x test
   ```

---

## 📝 ПРИМЕЧАНИЯ

- **NeoForge версия:** 1.21
- **Java версия:** 21
- **Тип клиента:** clientside-only (OnlyIn Dist.CLIENT)
- **Reflection использует:** AOTReflect.java (для доступа к закрытым классам Danny's AOT jar)
- **Физический движок:** AOT's own (не переписываем, только перехватываем результат)

---

## 🎓 ВЫВОДЫ

**Почему были ошибки в начале:**
- Я прочитал неправильные документы (Stage 2 spec вместо insruchia.txt)
- insruchia.txt - это ТОЧНОЕ описание от пользователя что не работает
- Это не просто баги, а **архитектурные проблемы** которые требуют переписания Physics Controller'а

**Что нужно помнить:**
- Система тросов должна работать как **точка опоры, а не двигатель**
- Space и Shift - это INPUT для подтягивания/ослабления
- Физика натяжения - это **сфера вокруг крюка**, а не линейное притяжение
- Сохранение импульса - **самое важное** для ощущения ODM
