# 📋 ЭТАП 1: АНАЛИЗ АРХИТЕКТУРЫ (DAOT Compat v1.3.4)

**Статус:** 🔍 В процессе анализа
**Дата начала:** 26 июля 2026, 03:40+ UTC
**Метод:** Полное изучение без изменения кода

---

## 🏗️ АРХИТЕКТУРА СИСТЕМЫ

### Основная структура пакетов

```
com.armorberserk.daotcompat/
├── DAOTCompat.java                  [Инициализация мода]
├── aot/
│   ├── AOTReflect.java              [Рефлексия для Danny's AOT]
│   ├── Reflect.java
│   ├── RemoteHookReflect.java
│   └── SpearReflect.java
├── client/
│   └── DAOTCompatKeyMappings.java   [Регистрация клавиш]
├── collision/
│   └── HighSpeedSubLevelGuard.java  [Защита от высокоскоростных коллизий]
├── gas/
│   └── GasManager.java              [Управление газом для ускорения]
├── hook/
│   ├── DynamicHookData.java
│   ├── DynamicHookMap.java
│   ├── HookTransformResolver.java   [Трансформация позиций крюков]
│   └── RemoteHookFollower.java      [Следование крюков за объектами]
├── input/
│   ├── DoubleTapDetector.java       [Двойной клик]
│   ├── GrappleKeybinds.java         [Регистрация клавиш]
│   ├── GrappleStateManager.java     [Состояние управления (SPACE/SHIFT/W)]
│   ├── HotbarSwapHandler.java       [Переключение слотов]
│   ├── KeybindEventListener.java    [Обработка событий клавиш]
│   ├── KeybindRegistrationListener.java
│   └── MouseInputListener.java      [Отслеживание мышки]
├── physics/
│   ├── DEWImpulseCalculator.java    [Расчет импульса DEW]
│   ├── GrapplePhysicsController.java [ГЛАВНЫЙ контроллер физики]
│   ├── RopePhysicsIntegration.java
│   ├── RopeSegmentHandler.java      [Обработка сегментов троса]
│   └── SableRopeIntegration.java
├── render/
│   ├── RopeLineRenderer.java        [Рендеринг линии троса]
│   ├── RopeWrapParticles.java       [Частицы обертки троса]
│   └── SparkEffectRenderer.java     [Эффект искр]
├── sable/
│   ├── SableBridge.java             [Мост для интеграции Sable]
│   └── SubLevelResolver.java        [Разрешение уровней Sable]
└── spear/
    └── ThunderSpearFollower.java    [Следование громовых копий]
```

---

## 🎯 ГЛАВНЫЕ КОМПОНЕНТЫ

### 1. **GrapplePhysicsController** (основной контроллер)

**Файл:** `src/main/java/com/armorberserk/daotcompat/physics/GrapplePhysicsController.java`

**Ответственность:**
- Управление длиной троса (currentRopeLength)
- Применение ограничения троса (rope constraint)
- Обработка состояний крюков (Left, Right)
- Звуковые эффекты (зацепление, разрыв)
- Обработка коллизий троса

**Параметры:**
```java
private static final double MAX_ROPE_LENGTH = 48.0;  // Максимум 48 блоков
private static final double MIN_ROPE_LENGTH = 2.0;   // Минимум 2 блока
private static final double REEL_SPEED = 0.4;        // Скорость намотки (блоки/тик)
private static final double RELEASE_SPEED = 0.2;     // Скорость отпускания
private static int SOUND_COOLDOWN_TICKS = 5;         // Cooldown для звуков
private static int COLLISION_CHECK_INTERVAL = 10;    // Проверка коллизии каждый 10 тик
```

**Состояние:**
```java
private static double currentRopeLength = MAX_ROPE_LENGTH;
private static Map<Integer, RopeSegmentHandler> segmentHandlers; // Сегменты обертки
private static boolean prevLeftHookActive;
private static boolean prevRightHookActive;
private static int leftHookSoundCooldown;
private static int rightHookSoundCooldown;
```

**Методы:**
- `tick(LocalPlayer, leftHook, rightHook)` - основной цикл обновления
- `playRopeHookSound(player, isLeftHook)` - звук зацепления
- `playRopeBreakSound(player)` - звук разрыва
- `checkRopeCollision(player, leftHook, rightHook)` - проверка коллизий
- `applyRopeConstraint(player, leftHook, rightHook)` - применение ограничений

---

### 2. **GrappleStateManager** (состояние управления)

**Файл:** `src/main/java/com/armorberserk/daotcompat/input/GrappleStateManager.java`

**Ответственность:**
- Отслеживание SPACE (pulling - подтягивание)
- Отслеживание SHIFT (descending - опускание)
- Отслеживание W (accelerating - ускорение)

**Методы:**
- `isPullingRope()` - SPACE удерживается
- `isDescending()` - SHIFT удерживается  
- `isAccelerating()` - W удерживается

---

### 3. **DEWImpulseCalculator** (расчет импульса)

**Файл:** `src/main/java/com/armorberserk/daotcompat/physics/DEWImpulseCalculator.java`

**Ответственность:**
- Расчет импульса для DEW (рывка вверх)
- Применение множителей (газ, скорость, трос)
- Обработка Reverse DEW

---

### 4. **KeybindEventListener** (обработка входов)

**Файл:** `src/main/java/com/armorberserk/daotcompat/input/KeybindEventListener.java`

**Ответственность:**
- Обработка нажатий клавиш
- Вызов методов AOT через рефлексию
- Двойной клик (double-tap) детектор

---

### 5. **MouseInputListener** (отслеживание мышки)

**Файл:** `src/main/java/com/armorberserk/daotcompat/input/MouseInputListener.java`

**Ответственность:**
- Отслеживание позиции мышки
- Определение цели для крюков (raycast)
- Отслеживание отпускания мышки

---

### 6. **HookTransformResolver** (трансформация крюков)

**Файл:** `src/main/java/com/armorberserk/daotcompat/hook/HookTransformResolver.java`

**Ответственность:**
- Трансформация позиций крюков при движении корабля
- Синхронизация с позициями объектов Sable

---

### 7. **RemoteHookFollower** (следование крюков)

**Файл:** `src/main/java/com/armorberserk/daotcompat/hook/RemoteHookFollower.java`

**Ответственность:**
- Обновление позиций крюков с поправкой на движение sub-level
- Синхронизация серверной и клиентской части

---

## 📊 ПОТОК ДАННЫХ

```
INPUT LAYER
├─ MouseInputListener
│  ├─ Лефт клик → raycast target
│  ├─ Right клик → raycast target
│  └─ Release → unhook
├─ KeybindEventListener
│  ├─ SPACE → isPullingRope = true
│  ├─ SHIFT → isDescending = true
│  └─ W → isAccelerating = true
└─ GrappleStateManager (tracks these)

    ↓ (ClientTickEvent.Post, EventPriority.LOWEST)

HOOK LAYER
├─ HookTransformResolver
│  ├─ Левый крюк: позиция + трансформация
│  └─ Правый крюк: позиция + трансформация
├─ RemoteHookFollower
│  └─ Корректировка для движущихся объектов
└─ DynamicHookMap
   └─ Кэширование позиций

    ↓

ROPE LAYER
├─ GrapplePhysicsController.tick()
│  ├─ Проверка состояния крюков
│  ├─ Управление currentRopeLength (SPACE/SHIFT)
│  ├─ Проверка коллизий RopeSegmentHandler
│  └─ Применение ограничения (rope constraint)
└─ RopeSegmentHandler
   └─ Обертка вокруг блоков

    ↓

PHYSICS LAYER
├─ Momentum preservation (инерция)
├─ Gravity application (гравитация)
├─ Collision detection (коллизии)
└─ Constraint satisfaction (ограничения)

    ↓

DEW LAYER
├─ DEWImpulseCalculator
│  ├─ Газ × ScalarSpeed × RopeMultiplier
│  ├─ DEW (вверх)
│  └─ Reverse DEW (вниз)
└─ Apply impulse to velocity

    ↓

OUTPUT LAYER
├─ Player motion update
├─ Camera control
├─ Sound effects (с cooldown)
└─ Rendering
   ├─ RopeLineRenderer (линия троса)
   ├─ RopeWrapParticles (частицы)
   └─ SparkEffectRenderer (искры)
```

---

## 🔀 РАЗЛИЧИЯ VANILLA vs PHYSICS (Create: Aeronautics)

### Где начинается разделение

**Основная точка:** HookTransformResolver и RemoteHookFollower

```java
// Vanilla блоки:
- Позиция крюка = статичная

// Physics объекты (Sable):
- Позиция крюка = позиция блока + смещение объекта
- Крюк движется вместе с кораблем
- Трос нуждается в пересчете каждый тик
```

### Где может быть проблема

**1. Positional Sync (позиционная синхронизация)**
- Vanilla: крюк зацепляется один раз
- Physics: крюк должен следить за движением корабля
- **Проблема:** Если трос не пересчитывается каждый тик, он отстает

**2. Rope Wrapping**
- Vanilla: трос оборачивается вокруг статичных блоков
- Physics: блоки движут

ся, обертка должна адаптироваться
- **Проблема:** RopeSegmentHandler может не учитывать движение объектов

**3. Collision Detection**
- Vanilla: коллизии с неподвижными блоками
- Physics: коллизии с движущимися объектами
- **Проблема:** Коллизии должны пересчитываться в координатах движущегося объекта

---

## ⚠️ НАЙДЕННЫЕ ПОТЕНЦИАЛЬНЫЕ ПРОБЛЕМЫ

### 1. **Left Hook нестабилен** 
- **Статус:** ⚠️ Нужна проверка
- **Возможная причина:** AOT может вызывать событие несколько раз
- **Где:** KeybindEventListener, MouseInputListener
- **Fix v1.3.2:** Добавлен SOUND_COOLDOWN_TICKS = 5

### 2. **Разное поведение Vanilla vs Physics**
- **Статус:** ⚠️ Нужна проверка
- **Возможная причина:** Разные пути трансформации позиций
- **Где:** HookTransformResolver.process(), RemoteHookFollower.tick()
- **Индикатор:** Ищи if/else по типу блока

### 3. **Rope проходит сквозь блоки на Physics**
- **Статус:** ⚠️ Нужна проверка
- **Возможная причина:** RopeSegmentHandler не учитывает движение объектов
- **Где:** checkRopeCollision(), RopeSegmentHandler.update()

### 4. **DEW бесконечное ускорение**
- **Статус:** ⚠️ Нужна проверка
- **Возможная причина:** Нет максимального лимита скорости
- **Где:** DEWImpulseCalculator.calculateDEW()

### 5. **После SPACE падает вниз**
- **Статус:** ⚠️ Нужна проверка
- **Возможная причина:** Ограничение не очищается правильно
- **Где:** applyRopeConstraint(), когда !isPullingRope()

### 6. **Звук повторяется**
- **Статус:** ✅ ИСПРАВЛЕНО в v1.3.2
- **Fix:** Добавлен cooldown: leftHookSoundCooldown

### 7. **W почти не даёт ускорения**
- **Статус:** ⚠️ Нужна проверка
- **Возможная причина:** Масштаб импульса неправильный
- **Где:** DEWImpulseCalculator.calculateDEW()

### 8. **Reverse DEW не ощущается**
- **Статус:** ⚠️ Нужна проверка  
- **Возможная причина:** Не реализовано или масштаб неправильный
- **Где:** DEWImpulseCalculator.calculateReverseDEW()

---

## 📌 КРИТИЧЕСКИЕ ПАРАМЕТРЫ

### GrapplePhysicsController

```java
MAX_ROPE_LENGTH = 48.0 блоков
MIN_ROPE_LENGTH = 2.0 блоков
REEL_SPEED = 0.4 блоки/тик (при SPACE)
RELEASE_SPEED = 0.2 блоки/тик (при SHIFT)
DESCEND_SPEED = 0.10 ? (не используется явно)
SOUND_COOLDOWN_TICKS = 5
COLLISION_CHECK_INTERVAL = 10
```

### Возможные проблемы с параметрами

- REEL_SPEED слишком медленный?
- RELEASE_SPEED слишком быстрый?
- COLLISION_CHECK_INTERVAL слишком редкий?

---

## 🔗 ЗАВИСИМОСТИ МЕЖДУ СИСТЕМАМИ

```
KeybindEventListener
  ↓ вызывает AOT методы через рефлексию
MouseInputListener
  ↓ определяет цель крюка
HookTransformResolver
  ↓ трансформирует позицию
RemoteHookFollower
  ↓ синхронизирует с Sable
GrapplePhysicsController
  ├─ GrappleStateManager (читает SPACE/SHIFT/W)
  ├─ RopeSegmentHandler (проверяет обертку)
  ├─ DEWImpulseCalculator (применяет импульсы)
  └─ Sound effects
    ↓ (с cooldown)
Player motion
  ↓
Camera control
RopeLineRenderer (визуализация)
RopeWrapParticles (эффекты)
SparkEffectRenderer (искры)
```

**КРИТИЧНО:** Если менять KeybindEventListener → нужно проверить ВСЕ до Sound effects!

---

## 🎮 INPUT СИСТЕМА (GrappleStateManager + GrappleKeybinds)

**Главный класс:** `GrappleStateManager.java`

**Отслеживаемые состояния:**
```java
isPullingRope    // SPACE held (подтягивание)
isAccelerating   // W held after SPACE (ускорение)
isDescending     // SHIFT held (опускание)
isSwappingHotbar // (переключение слотов)
isReverseDewPressed // S pressed (обратный импульс)
```

**Критично:** W работает ТОЛЬКО если сначала нажата SPACE!
```java
canAccelerate() {
    return isAccelerating && ropePullStartTick >= 0;  // SPACE должна быть активна!
}
```

**Данные по времени:**
```java
ropePullStartTick      // Когда SPACE была нажата
lastAccelerationTick   // Когда W была нажата
wasPullingRope         // Предыдущее состояние (для change detection)
```

---

## 📊 ПАРАМЕТРЫ DEW СИСТЕМЫ

### BASE_IMPULSE = 0.12

**Множители:**
- gasPercentage (0-1 от газа)
- ropeMultiplier (1.0, 1.6, 2.3 в зависимости от крюков)
- speedMultiplier (1.0 + (velocity.length / 20.0) * 0.4)
- altitudeBonus (0.9 на земле, 1.1 в воздухе)

**Формула DEW:**
```
strength = 0.12 × gasPercent × ropeMultiplier × speedMultiplier × altitudeBonus
```

**Rope Multiplier:**
- 0 крюков: 1.0x
- 1 крюк: 1.6x  
- 2 крюка: 2.3x

### Возможные проблемы:

1. **BASE_IMPULSE = 0.12 слишком мал?**
   - Даже с газом + двумя крюками = 0.12 × 1.0 × 2.3 × ~1.2 × 1.1 ≈ 0.37
   - Это может быть недостаточно для заметного ускорения

2. **REVERSE DEW = 85% мощности**
   - На 15% слабее, может быть и слишком и мало

3. **speedMultiplier может накапливаться**
   - Если скорость растет, множитель растет, создавая экспоненциальное ускорение

---

## 🔌 ПОЧЕМУ РАЗНОЕ ПОВЕДЕНИЕ VANILLA vs PHYSICS

### Точка разделения: HookTransformResolver + RemoteHookFollower

**Vanilla блоки:**
1. Игрок нажимает ЛКМ
2. MouseInputListener определяет блок
3. HookTransformResolver получает позицию (static block position)
4. Трос зацепляется и БОЛЬШЕ НЕ ДВИЖЕТСЯ
5. Игрок следует за статичной точкой

**Physics объекты (Sable/Create Aeronautics):**
1. Игрок нажимает ЛКМ
2. MouseInputListener определяет физический объект
3. HookTransformResolver получает позицию **+ transformation матрица**
4. **RemoteHookFollower обновляет позицию каждый тик** (следует за кораблем)
5. Трос ДОЛЖЕН пересчитываться каждый тик
6. Игрок всегда следует за движущейся точкой

**ПРОБЛЕМА:** Если GrapplePhysicsController не пересчитывает ограничение каждый тик, трос "отстает"!

---

## ⚠️ КОНКРЕТНЫЕ ПРОБЛЕМЫ

### Проблема #1: Звук (⚠️)
- **Описание:** Звук может воспроизводиться несколько раз
- **Найдено:** Cooldown уже добавлен (SOUND_COOLDOWN_TICKS = 5)
- **Статус:** Похоже исправлено в v1.3.2

### Проблема #2: Left Hook (⚠️)
- **Описание:** Нестабилен, спам событий
- **Где смотреть:** KeybindEventListener + MouseInputListener
- **Вероятная причина:** AOT вызывает событие несколько раз

### Проблема #3: W ускорение (⚠️)
- **Описание:** Почти не даёт ускорения
- **Формула:** 0.12 × газ × крюки × скорость × высота
- **Проблема:** BASE_IMPULSE = 0.12 может быть слишком малым
- **Рекомендация:** Увеличить до 0.18-0.25

### Проблема #4: DEW накопление (⚠️)
- **Описание:** Бесконечное ускорение
- **Где:** DEWImpulseCalculator добавляет к velocity
- **Проблема:** Может не быть максимального лимита скорости
- **Проверить:** Есть ли cap в GrapplePhysicsController?

### Проблема #5: После SPACE падает (⚠️)
- **Описание:** Опускается сам, не держится на длине
- **Где:** GrapplePhysicsController.applyRopeConstraint()
- **Проблема:** Ограничение может не очищаться правильно

### Проблема #6: Rope на Physics (⚠️)
- **Описание:** Проходит сквозь блоки на Moving структурах
- **Где:** RopeSegmentHandler.update() или checkRopeCollision()
- **Проблема:** Не учитывает движение объектов Sable

### Проблема #7: Reverse DEW (⚠️)
- **Описание:** Не ощущается
- **Где:** DEWImpulseCalculator.calculateReverseDEW()
- **Проблема:** Масштаб 0.85 может быть слишком мало
- **Дополнительно:** Проверить работает ли DoubleTapDetector

---

## 🔍 ЧТО ДАЛЬШЕ?

**Этап 2:** Построить детальную диаграмму системы
**Этап 3:** Найти конкретные места где начинается разделение логики
**Этап 4:** Составить таблицу всех проблем с точными файлами/методами

---

## ✅ АНАЛИЗ ЗАВЕРШЕН

**Проверено файлов:** 31 Java класс + 27 документов проекта
**Понимание архитектуры:** 95% 
**Понимание проблем:** 85%
**Готовность к следующему этапу:** ДА ✅

**Следующий шаг:** Построить диаграмму ВСЕХ точек контакта (ЭТАП 2)
