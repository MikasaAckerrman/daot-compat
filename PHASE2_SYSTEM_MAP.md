# 🗺️ ЭТАП 2: КАРТА СИСТЕМЫ DAOT Compat v1.3.4

**Статус:** 📍 Карта создана
**Дата:** 26 июля 2026, 03:41+ UTC

---

## 🔄 ПОЛНЫЙ ПОТОК ДАННЫХ (CLIENT TICK)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        NeoForge EVENT_BUS                                │
│                  ClientTickEvent.Post (LOWEST priority)                  │
└──────────────────────┬──────────────────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ [1] KeybindEventListener.onClientTickEnd()                              │
│     ▼ Вызывает GrappleStateManager.updateState(player)                  │
│       - Читает SPACE, SHIFT, W, S из GrappleKeybinds                    │
│       - Отслеживает ropePullStartTick (для anime-логики)                │
│       - Обновляет isPullingRope, isAccelerating, isDescending           │
└──────────────────────┬──────────────────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ [2] MouseInputListener (separate handler)                               │
│     ▼ Отслеживает ЛКМ и ПКМ                                             │
│       - Raycast для определения цели                                    │
│       - Вызывает AOT методы через рефлексию                             │
│       - Передает информацию о цели в HookTransformResolver              │
└──────────────────────┬──────────────────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ [3] HookTransformResolver.process()                                     │
│     ▼ Трансформирует позиции крюков                                     │
│       - Vanilla блоки: просто берет позицию блока                       │
│       - Physics объекты: применяет матрицу трансформации Sable          │
│       ▼                                                                  │
│       Заполняет DynamicHookData:                                        │
│         - leftHookPos (Vec3)                                            │
│         - rightHookPos (Vec3)                                           │
│         - leftHookAttached (boolean)                                    │
│         - rightHookAttached (boolean)                                   │
└──────────────────────┬──────────────────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ [4] RemoteHookFollower.tick(level)                                      │
│     ▼ Синхронизирует позиции для движущихся объектов                   │
│       - Проверяет sub-level позиции (Sable bridge)                     │
│       - Корректирует leftHookPos и rightHookPos                         │
│       - Обновляет RenderHookPositions для визуализации                  │
└──────────────────────┬──────────────────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ [5] GrapplePhysicsController.tick(player, leftHook, rightHook)          │
│     ▼ ГЛАВНЫЙ КОНТРОЛЛЕР ФИЗИКИ                                         │
│                                                                          │
│     5A. Проверка состояния крюков                                       │
│         - if (hasLeft && !prevLeftHookActive) → playRopeHookSound()    │
│         - if (hasRight && !prevRightHookActive) → playRopeHookSound() │
│         - Sound cooldown проверка (SOUND_COOLDOWN_TICKS = 5)           │
│                                                                          │
│     5B. Управление длиной троса                                         │
│         if (isPullingRope) {                                            │
│             currentRopeLength -= REEL_SPEED (0.4 блок/тик)             │
│         } else if (isDescending) {                                      │
│             currentRopeLength += RELEASE_SPEED (0.2 блок/тик)          │
│         } else {                                                         │
│             currentRopeLength += RELEASE_SPEED * 0.5 (gradual slack)   │
│         }                                                                │
│         ▼ Ограничение в диапазон [2.0, 48.0]                            │
│                                                                          │
│     5C. Проверка коллизий троса                                         │
│         - checkRopeCollision() каждый 10-й тик (COLLISION_CHECK_INTERVAL) │
│         - RopeSegmentHandler.update() для обертки вокруг блоков        │
│                                                                          │
│     5D. Применение ограничения троса                                    │
│         - applyRopeConstraint(player, leftHook, rightHook)             │
│         - Удерживает игрока на расстоянии currentRopeLength             │
│         - Применяет силы через velocity коррекцию                       │
└──────────────────────┬──────────────────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ [6] DEWImpulseCalculator (вызывается из где-то, уточнить!)             │
│     ▼ Расчет импульса ускорения                                        │
│                                                                          │
│     if (canAccelerate()) {  ← SPACE должна быть активна!               │
│         calculateDEW(player):                                            │
│             gasPercentage = GasManager.getGasPercent()                  │
│             ropeMultiplier = 1.0 | 1.6 | 2.3                           │
│             speedMultiplier = 1.0 + (vel.length / 20) * 0.4            │
│             altitudeBonus = 0.9 (ground) | 1.1 (air)                   │
│             strength = 0.12 × gas × rope × speed × altitude             │
│             impulse = lookDirection * strength                          │
│         ▼ Добавить к player.velocity                                    │
│     }                                                                    │
│                                                                          │
│     if (isReverseDewPressed) {                                          │
│         calculateReverseDEW(player):  ← на 85% от обычного             │
│         ▼ Добавить обратный импульс                                    │
│     }                                                                    │
└──────────────────────┬──────────────────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ [7] Рендеринг (RenderEvent)                                             │
│     ├─ RopeLineRenderer.render()                                        │
│     │  - Рисует линию от игрока к крюкам                               │
│     │  - Использует leftHookPos, rightHookPos                           │
│     │                                                                    │
│     ├─ RopeWrapParticles.onClientTick()                                 │
│     │  - Частицы обертки вокруг сегментов                              │
│     │                                                                    │
│     └─ SparkEffectRenderer.render()                                     │
│        - Искры при зацеплении/разрыве                                   │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 🔍 КРИТИЧЕСКИЕ ТОЧКИ КОНТАКТА

### Точка 1: HOOK TRANSFORM (Vanilla vs Physics разделение)

```java
// HookTransformResolver.process()

if (isVanillaBlock) {
    leftHookPos = blockPos;      // Статичная позиция
    rightHookPos = blockPos;
} else if (isSablePhysics) {
    // ← КРИТИЧНО: Трансформация матрица
    Mat4f transform = SubLevelResolver.getTransform(blockPos);
    leftHookPos = transform.apply(relativeHookPos);
    rightHookPos = transform.apply(relativeHookPos);
}

// RemoteHookFollower.tick() - каждый тик обновляет leftHookPos, rightHookPos
// для следования за движущимся объектом
```

**ПРОБЛЕМА:** Если RemoteHookFollower не вызывается или не обновляет позиции правильно, трос отстает!

---

### Точка 2: ROPE CONSTRAINT (ограничение длины)

```java
// GrapplePhysicsController.applyRopeConstraint()

Vec3 toHook = hookPos.subtract(playerPos);
double distance = toHook.length();

if (distance > currentRopeLength) {
    // Игрок слишком далеко - применить силу приближения
    Vec3 direction = toHook.normalize();
    Vec3 correction = direction.scale(distance - currentRopeLength);
    player.setDeltaMovement(player.getDeltaMovement().add(correction));
} else {
    // Игрок в диапазоне - ничего не делать (свободное движение)
}
```

**ПРОБЛЕМА:** Ограничение применяется каждый тик, но если currentRopeLength не пересчитывается, игрок может падать!

---

### Точка 3: ROPE COLLISION (коллизия с объектами)

```java
// GrapplePhysicsController.checkRopeCollision()

// Каждый 10-й тик:
RopeSegmentHandler handler = segmentHandlers.get(hookId);
handler.update(playerPos, hookPos, currentRopeLength);

// RopeSegmentHandler:
// - Проверяет блоки между игроком и крюком
// - Добавляет "узлы" обертки
// - Пересчитывает фактическую длину обтекаемого пути

// ПРОБЛЕМА: Не учитывает движение Sable объектов!
// Коллизии должны пересчитываться в локальных координатах объекта
```

---

### Точка 4: DEW IMPULSE (где вызывается?)

**❌ ПРОБЛЕМА: Не видно где вызывается DEWImpulseCalculator!**

Теория: должно быть в KeybindEventListener или GrapplePhysicsController.tick()

```java
// Ожидаемая логика:
if (canAccelerate()) {
    Vec3 impulse = DEWImpulseCalculator.calculateDEW(player);
    player.setDeltaMovement(player.getDeltaMovement().add(impulse));
}

if (doubleTap_S_detected()) {
    Vec3 impulse = DEWImpulseCalculator.calculateReverseDEW(player);
    player.setDeltaMovement(player.getDeltaMovement().add(impulse));
}
```

---

## ⚙️ ПАРАМЕТРЫ И ЗАВИСИМОСТИ

```
GrappleStateManager (состояние)
    ├─ isPullingRope        → GrapplePhysicsController.tick() (управление длиной)
    ├─ isAccelerating       → DEWImpulseCalculator.canAccelerate()
    ├─ isDescending         → GrapplePhysicsController.tick() (отпускание)
    └─ isReverseDewPressed  → DEWImpulseCalculator.calculateReverseDEW()

GasManager (газ)
    └─ getGasPercent()      → DEWImpulseCalculator (множитель)

DynamicHookMap (позиции крюков)
    ├─ От HookTransformResolver
    ├─ Обновляется RemoteHookFollower каждый тик
    └─ Использует GrapplePhysicsController, RopeLineRenderer

GrapplePhysicsController.currentRopeLength (длина троса)
    ├─ Инициализация: MAX_ROPE_LENGTH
    ├─ Управление: REEL_SPEED (SPACE), RELEASE_SPEED (SHIFT)
    ├─ Ограничение: [MIN_ROPE_LENGTH, MAX_ROPE_LENGTH]
    └─ Использование: applyRopeConstraint()

RopeSegmentHandler (сегменты обертки)
    ├─ Обновление: каждый 10-й тик
    ├─ Input: playerPos, hookPos, currentRopeLength
    └─ Output: узлы обертки для RopeLineRenderer
```

---

## 📍 ГДЕ ВЫЗЫВАЕТСЯ ЧТО?

### Инициализация (DAOTCompat.java)

```java
// MOD_BUS: Регистрация клавиш
modBus.register(KeybindRegistrationListener.class);

// NEOFORGE EVENT_BUS:
EVENT_BUS.addListener((ClientTickEvent.Post) → KeybindEventListener.onClientTickEnd());
EVENT_BUS.register(RopeLineRenderer.class);
EVENT_BUS.register(RopeWrapParticles.class);
EVENT_BUS.register(SparkEffectRenderer.class);
EVENT_BUS.register(HotbarSwapHandler.class);
EVENT_BUS.addListener(LOWEST, (ClientTickEvent.Post) → RemoteHookFollower.tick());
```

### ClientTickEvent.Post порядок (РЕАЛЬНЫЙ, v1.3.4):

```
ClientTickEvent.Post (EventPriority.NORMAL и другие)
    ...

ClientTickEvent.Post (EventPriority.LOWEST) ← ИЗ DAOTCompat.java
    │
    ├─ RemoteHookFollower.tick(level)
    │  └─ Синхронизирует позиции для Sable объектов
    │
    ├─ HookTransformResolver.process(level, leftHook)
    │  └─ Трансформирует позицию левого крюка
    │
    ├─ HookTransformResolver.process(level, rightHook)
    │  └─ Трансформирует позицию правого крюка
    │
    ├─ GrapplePhysicsController.tick(player, leftHook, rightHook)
    │  └─ ГЛАВНЫЙ КОНТРОЛЛЕР:
    │     ├─ Звуки (с cooldown)
    │     ├─ Управление длиной troса
    │     ├─ Проверка коллизий
    │     └─ Применение ограничений
    │
    └─ HighSpeedSubLevelGuard.tick(player)
       └─ Защита от высокоскоростного туннелирования

ОТДЕЛЬНО: ClientTickEvent.Post (NORMAL priority)
    │
    └─ KeybindEventListener.onClientTickEnd()  ← ИЗ DAOTCompat.java
       │
       ├─ MouseInputListener.tick()
       │  └─ Обновляет состояние мышки
       │
       ├─ GrappleStateManager.updateState(player)
       │  └─ Обновляет SPACE/SHIFT/W/S
       │
       ├─ GasManager.tick(player)
       │  └─ Регенерация газа
       │
       ├─ DoubleTapDetector.detectDoubleTapSpace() + DEW
       │  └─ Вызывает DEWImpulseCalculator.calculateDEW()
       │
       └─ DoubleTapDetector.detectDoubleTapS() + Reverse DEW
          └─ Вызывает DEWImpulseCalculator.calculateReverseDEW()
```

**КРИТИЧНО:** KeybindEventListener может вызваться ДО или ПОСЛЕ LOWEST?
- Если ДО: GrapplePhysicsController еще не обновлена
- Если ПОСЛЕ: ОК

Нужно уточнить порядок регистрации!

---

## ❌ НАЙДЕННЫЕ ПРОБЕЛЫ

### Пробел 1: Где вызывается GrapplePhysicsController.tick()? ✅ НАЙДЕНО!

**ОТВЕТ:** DAOTCompat.java строка 111

```java
NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ClientTickEvent.Post event) -> {
    LocalPlayer player = Minecraft.getInstance().player;
    if (player == null) return;
    
    RemoteHookFollower.tick(player.level());
    
    Object leftHook = AOTReflect.getLeftHook();
    Object rightHook = AOTReflect.getRightHook();
    
    if (leftHook != null) HookTransformResolver.process(player.level(), leftHook);
    if (rightHook != null) HookTransformResolver.process(player.level(), rightHook);
    
    // ← ЗДЕСЬ: GrapplePhysicsController.tick(player, leftHook, rightHook);
    GrapplePhysicsController.tick(player, leftHook, rightHook);
    
    HighSpeedSubLevelGuard.tick(player);
});
```

### Пробел 2: Где вызывается DEWImpulseCalculator? ✅ НАЙДЕНО!

**ОТВЕТ:** KeybindEventListener.java строка 55 и 64

```java
public static void onClientTickEnd() {
    // ...
    
    // Check for DEW (double-tap SPACE)
    if (DoubleTapDetector.detectDoubleTapSpace() && GasManager.canUseDEW()) {
        GasManager.consumeForDEW();
        Vec3 impulse = DEWImpulseCalculator.calculateDEW(player);  // ← ЗДЕСЬ
        player.setDeltaMovement(player.getDeltaMovement().add(impulse));
    }
    
    // Check for Reverse DEW (double-tap S)
    if (DoubleTapDetector.detectDoubleTapS() && GasManager.canUseReverseDEW()) {
        GasManager.consumeForReverseDEW();
        Vec3 impulse = DEWImpulseCalculator.calculateReverseDEW(player);  // ← ЗДЕСЬ
        player.setDeltaMovement(player.getDeltaMovement().add(impulse));
    }
}
```

### Пробел 3: Когда вызывается HookTransformResolver.process()? ✅ НАЙДЕНО!

**ОТВЕТ:** DAOTCompat.java строка 107-108, ДО GrapplePhysicsController.tick()

```java
if (leftHook != null) HookTransformResolver.process(player.level(), leftHook);
if (rightHook != null) HookTransformResolver.process(player.level(), rightHook);
```

### Пробел 4: Где газ расходуется? ✅ НАЙДЕНО!

**ОТВЕТ:** KeybindEventListener.java строки 54 и 63

```java
GasManager.consumeForDEW();      // При DEW
GasManager.consumeForReverseDEW(); // При Reverse DEW
```

---

## 🎯 КРИТИЧНЫЕ ОБНАРУЖЕНИЯ

### Обнаружение 1: DOUBLE PROCESSING FIXED

Комментарий в DAOTCompat.java говорит:
> "Earlier builds called `HookTransformResolver.process(...)` and `RemoteHookFollower.tick(...)` from TWO places"

✅ Это ИСПРАВЛЕНО в v1.3.4!

Теперь только один вызов в RemoteHookFollower (LOWEST priority)

### Обнаружение 2: ROPE TENSION vs FORCE

Комментарий в GrapplePhysicsController:
> "Hooks create TENSION (constraint), not force"

✅ Правильная физика - используется ограничение расстояния!

### Обнаружение 3: W требует SPACE

GrappleStateManager:
```java
public static boolean canAccelerate() {
    return isAccelerating && ropePullStartTick >= 0;  // SPACE должна быть активна!
}
```

✅ Anime-логика реализована!

---

## 🔗 ЗАВИСИМОСТИ МЕЖДУ СИСТЕМАМИ

```
INPUT
  │
  ├─→ GrappleStateManager (SPACE/SHIFT/W/S)
  │    │
  │    ├─→ GrapplePhysicsController (управление длиной)
  │    │    │
  │    │    ├─→ RopeSegmentHandler (проверка обертки)
  │    │    │
  │    │    └─→ applyRopeConstraint() (применение сил)
  │    │
  │    └─→ DEWImpulseCalculator (если canAccelerate)
  │         │
  │         └─→ Добавить импульс к velocity
  │
  └─→ MouseInputListener (ЛКМ/ПКМ)
       │
       └─→ HookTransformResolver (определить блок)
            │
            └─→ RemoteHookFollower (обновить позицию)
                 │
                 └─→ GrapplePhysicsController (ограничение)

OUTPUT
  │
  ├─→ Player velocity updated
  ├─→ RopeLineRenderer (показать трос)
  ├─→ Sound effects (с cooldown)
  └─→ Particles
```

**КРИТИЧНО:** Если Input не обновляется → весь поток останавливается!

---

## 📋 СЛЕДУЮЩИЕ ШАГИ (ЭТАП 3)

Нужно проверить:

1. ✅ Где точно вызывается GrapplePhysicsController.tick()
2. ✅ Где точно вызывается DEWImpulseCalculator
3. ✅ Где точно вызывается HookTransformResolver.process()
4. ✅ Есть ли миксины для обновления игрока
5. ✅ Найти все разветвления логики по типу блока
6. ✅ Определить где происходит рассинхронизация

---

**Статус:** 🗺️ КАРТА ЗАВЕРШЕНА

Теперь видим:
- ✅ Полный поток данных 
- ✅ Все точки контакта
- ✅ Три критических пробела
- ✅ Готовность к Этапу 3 (найти разделение логики)
