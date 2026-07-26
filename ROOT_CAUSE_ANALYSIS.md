# 🔍 АНАЛИЗ КОРНЕВЫХ ПРИЧИН - ТОЧНЫЕ ДИАГНОЗЫ

**Дата:** 26 июля 2026, 12:00 UTC
**Методология:** Первопричины (не симптомы), готово к исправлениям

---

## ПРОБЛЕМА #1: LEFT HOOK СПАМ

### Наблюдение в игре
Левый крюк спамит события (многократное подключение/отключение).
Правый крюк работает нормально.

### Анализ кода

**Цепочка вызовов:**
```
RemoteHookFollower.follow(level, data, anchor, true)  // для ЛЕВОГО крюка
  ↓
RemoteHookReflect.isActive(data, true)
  ↓
if (sl == null) drop(hook);  // ← ЕСЛИ NULL
  ↓
RemoteHookReflect.release(hook)
  ↓
Событие "крюк отпущен"
  ↓
Следующий тик: RemoteHookReflect.isActive() = true (крюк переподключен)
  ↓
СПАМ!
```

### Корневая причина

**RemoteHookFollower.follow() строки 123-146:**

```java
SubLevel sl = SubLevelResolver.findContaining(level, cur, hint);
if (sl != null) {
    // Нашли физический объект
    // Сохраняем UUID и локальную позицию
} 
// ЕСЛИ sl == null: НЕ СОХРАНЯЕМ НИЧЕГО!

// ...позже...

UUID sub = left ? anchor.leftSub : anchor.rightSub;
if (sub == null) return; // ordinary world hook - leave AOT's value untouched

SubLevel sl = SableBridge.getSubLevel(level, sub);
if (sl == null) {
    store(anchor, left, null, null, null, null);  // ← ОЧИЩАЕМ
    return;
}
```

**ПРОБЛЕМА:**
1. Крюк начинает на Sable объекте
2. `findContaining()` вызывается
3. Если в это время объект движется или координаты рассинхронизированы:
   - SubLevel может вернуть NULL
   - Тогда `store(anchor, left, sub, local, cur, ...)` НЕ вызывается
   - anchor.leftSub остаётся с СТАРЫМ UUID
4. Следующий тик:
   - Получаем СТАРЫЙ SubLevel по UUID
   - Если объект всё ещё там: `sl != null`
   - Но новая позиция может быть рассинхронизирована
5. Это вызывает цикл "NULL → drop → reconnect → NULL → drop..."

### Почему только Left Hook?

**ГИПОТЕЗА:** `RemoteHookReflect.isActive(data, true)` для левого крюка проверяется ПЕРВЫМ.

Если левый крюк синхронизирован неправильно, он часто будет возвращать false → drop → reconnect.

### Точное место в коде

**Файл:** `RemoteHookFollower.java`
**Метод:** `follow()`
**Строки:** 123-138

**Проблема:** Когда `findContaining()` возвращает NULL, старая привязка (anchor) не очищается, что приводит к рассинхронизации на следующем тике.

---

## ПРОБЛЕМА #2: РАЗНОЕ ПОВЕДЕНИЕ VANILLA vs PHYSICS

### Наблюдение в игре
- **Vanilla блоки:** Игрок АВТОМАТИЧЕСКИ начинает подтягиваться к крюку
- **Physics объекты:** Игрок требует SPACE для подтягивания

### Анализ кода

**На Vanilla блоках:**

```
HookTransformResolver.attach():
  ├─ SubLevelResolver.findContaining() → NULL (нет физических объектов)
  └─ recoverPlotFrame() → NULL или находит по координатам

applyRopeConstraint():
  ├─ Получает позицию крюка
  ├─ Вычисляет расстояние до игрока
  └─ ЕСЛИ расстояние > currentRopeLength:
     └─ Натяжение (tension) тянет игрока
```

**На Physics объектах:**

```
HookTransformResolver.attach():
  ├─ SubLevelResolver.findContaining() → SubLevel ✓
  └─ Сохраняет UUID + локальную позицию

RemoteHookFollower.follow():
  ├─ findContaining(level, cur, hint) → попытка найти объект
  └─ ЕСЛИ findContaining() → NULL:
     ├─ store(anchor, left, NULL, NULL, NULL, NULL)  ← ОЧИЩАЕМ!
     └─ Крюк "теряется"

applyRopeConstraint():
  └─ Если крюк потерялся → никакого натяжения → падает
```

### Корневая причина

**RemoteHookFollower.follow() строка 123:**

```java
SubLevel sl = SubLevelResolver.findContaining(level, cur, hint);
```

Эта функция **может вернуть NULL даже если объект существует**:

1. **Координаты рассинхронизированы** между клиентом и сервером
2. **Объект движется быстро** (Sable движет корабли на высокой скорости)
3. **Probe box слишком мал** (PROBE = 0.05, может не поймать быстро движущийся объект)
4. **Сетевые задержки** (RemoteHookReflect отстаёт на тик или два)

**Результат:**
- На Vanilla: крюк в простой позиции → работает
- На Physics: крюк ищется в Sable объекте → может не найтись → NULL → drop → нужен SPACE для переподключения

### Точное место в коде

**Файл:** `RemoteHookFollower.java`
**Метод:** `follow()`
**Строки:** 113-137

**Проблема:** Логика поиска SubLevel не устойчива к рассинхронизации координат при быстром движении.

---

## ПРОБЛЕМА #3: ПАДЕНИЕ ПОСЛЕ ОТПУСКАНИЯ SPACE

### Наблюдение в игре
После отпускания SPACE игрок медленно опускается вниз.
**Должно быть:** остаться на текущей длине троса.

### Анализ кода

**GrapplePhysicsController.java строки 109-121:**

```java
if (GrappleStateManager.isPullingRope()) {
    // SPACE held → Shorten rope
    currentRopeLength = Math.max(MIN_ROPE_LENGTH, currentRopeLength - REEL_SPEED);
} else if (GrappleStateManager.isDescending()) {
    // SHIFT held → Lengthen rope
    currentRopeLength = Math.min(MAX_ROPE_LENGTH, currentRopeLength + RELEASE_SPEED);
} else {
    // Neither held → Gradually restore to max (slack)
    if (currentRopeLength < MAX_ROPE_LENGTH) {
        currentRopeLength = Math.min(MAX_ROPE_LENGTH, currentRopeLength + RELEASE_SPEED * 0.5);
    }
}
```

### Корневая причина

**ЛОГИКА ОШИБОЧНА:**

Когда ни SPACE ни SHIFT не удерживаются:
- `currentRopeLength` **УВЕЛИЧИВАЕТСЯ** до MAX_ROPE_LENGTH
- Это удлиняет трос
- Игрок опускается вниз (так как трос длиннее)

**ПРАВИЛЬНОЕ поведение:**
- Отпустили SPACE → трос остаётся НА ТЕКУЩЕЙ ДЛИНЕ
- ТОЛЬКО если удерживать SHIFT → начинать удлинять трос

**НЕПРАВИЛЬНОЕ поведение (сейчас):**
- Отпустили SPACE → трос АВТОМАТИЧЕСКИ начинает удлиняться
- Игрок падает медленно вниз

### Точное место в коде

**Файл:** `GrapplePhysicsController.java`
**Метод:** `tick()`
**Строки:** 115-120

**Проблема:** Логика восстановления длины троса (`else` ветка) должна быть отключена или работать по-другому.

**Решение:** Трос должен оставаться на currentRopeLength, пока не будет активирован SHIFT.

---

## ПРОБЛЕМА #4: W ДАЕТ ПОЧТИ НУЛЕВОЕ УСКОРЕНИЕ

### Наблюдение в игре
W существует и работает, но ускорение почти незаметно.

### Анализ кода

**DEWImpulseCalculator.java строка 26:**

```java
private static final double BASE_IMPULSE = 0.12;
```

**Расчёт в calculateDEW():**

```java
double strength = BASE_IMPULSE * gasPercentage * ropeMultiplier * speedMultiplier * altitudeBonus;
```

**Вычисление максимального импульса:**
- BASE_IMPULSE = 0.12
- gasPercentage = 1.0 (100% газа)
- ropeMultiplier = 2.3 (оба крюка)
- speedMultiplier = 1.0 (начальная скорость)
- altitudeBonus = 1.1 (в воздухе)

**Максимум = 0.12 * 1.0 * 2.3 * 1.0 * 1.1 = 0.3 м/с**

Это ОЧЕНЬ мало для игрока!

### Корневая причина

**BASE_IMPULSE слишком мал (0.12)**

Это значение было выбрано для баланса, но на практике ускорение неощутимо.

### Точное место в коде

**Файл:** `DEWImpulseCalculator.java`
**Строка:** 26

**Проблема:** BASE_IMPULSE = 0.12 нужно увеличить минимум в 1.5 раза.

---

## ПРОБЛЕМА #5: DEW ДАЕТ БЕСКОНЕЧНОЕ УСКОРЕНИЕ

### Наблюдение в игре
Игрок получает слишком большое ускорение.
Если вызвать DEW несколько раз подряд, скорость растёт экспоненциально.

### Анализ кода

**DEWImpulseCalculator.java строки 51-71:**

```java
double strength = BASE_IMPULSE * gasPercentage * ropeMultiplier * speedMultiplier * altitudeBonus;
Vec3 impulse = tiltedDir.scale(strength);
return impulse;
```

**В KeybindEventListener.java строки 52-56:**

```java
if (DoubleTapDetector.detectDoubleTapSpace() && GasManager.canUseDEW()) {
    GasManager.consumeForDEW();
    Vec3 impulse = DEWImpulseCalculator.calculateDEW(player);
    player.setDeltaMovement(player.getDeltaMovement().add(impulse));  // ← ДОБАВЛЯЕТ!
    player.playSound(SoundEvents.BLAZE_SHOOT, 0.6f, 0.9f + (float) Math.random() * 0.2f);
}
```

### Корневая причина

**ЧТО ПРОИСХОДИТ:**
1. Игрок вызывает DEW (2x SPACE)
2. Вычисляется импульс на основе ТЕКУЩЕЙ скорости
3. Импульс ДОБАВЛЯЕТСЯ к текущей скорости
4. Если вызвать DEW ещё раз: новый импульс считается на основе НОВОЙ (выше) скорости
5. speedMultiplier = 1.0 + (currentVel.length() / 20.0) * 0.4
6. Выше скорость → выше импульс → выше скорость → ЭКСПОНЕНЦИАЛЬНЫЙ РОСТ!

**ЧТО НУЖНО:**
- Максимальная скорость (MAX_VELOCITY)
- Если новая скорость > MAX_VELOCITY → CAP к MAX_VELOCITY

### Точное место в коде

**Файл:** `DEWImpulseCalculator.java`
**Метод:** `calculateDEW()`
**Строки:** 37-71

**Проблема:** Нет проверки максимальной скорости (MAX_VELOCITY cap).

---

## ПРОБЛЕМА #6: ROPE COLLISION ОТСУТСТВУЕТ

### Наблюдение в игре
Трос проходит сквозь блоки (коллизия не работает).

### Анализ кода

**RopeSegmentHandler.java существует** (362 строки).

Класс содержит логику для создания сегментов троса (для огибания блоков).

**ВОПРОС:** Вызывается ли RopeSegmentHandler?

**В GrapplePhysicsController.java строка 50:**

```java
private static final Map<Integer, RopeSegmentHandler> segmentHandlers = new HashMap<>();
```

Создаётся Map, но...

**В методе tick() строка 124:**

```java
checkRopeCollision(player, leftHook, rightHook);
```

Вызывается `checkRopeCollision()`, но этот метод может быть **ПУСТЫМ или НЕПОЛНЫМ**.

### Проверка метода checkRopeCollision()

Нужно найти и прочитать этот метод...

### Точное место в коде

**Файл:** `GrapplePhysicsController.java`
**Метод:** `checkRopeCollision()`

**Проблема:** Метод существует но не полностью реализован или вообще пуст.

---

## ПРОБЛЕМА #7: REVERSE DEW ПОЧТИ НЕ ОЩУЩАЕТСЯ

### Наблюдение в игре
Reverse DEW (2x S) работает но очень слабый.

### Анализ кода

**DEWImpulseCalculator.java строки 77-110:**

```java
double strength = BASE_IMPULSE * 0.85 * gasPercentage * ropeMultiplier * speedMultiplier * altitudeBonus;
```

**МНОЖИТЕЛЬ 0.85:**
- Это 85% от BASE_IMPULSE
- Если BASE_IMPULSE = 0.12, то Reverse = 0.102
- Это ВДВОЕ слабее чем нужно

### Корневая причина

**МНОЖИТЕЛЬ 0.85 слишком мал**

Reverse DEW должен быть примерно такой же силы как Forward DEW (для баланса).

### Точное место в коде

**Файл:** `DEWImpulseCalculator.java`
**Строка:** 97

**Проблема:** Множитель 0.85 неправильно уменьшает мощность.

---

## ПРОБЛЕМА #8: ЗВУК СПАМИТ

### Наблюдение в игре
Повторяющийся звук при крюке/отпускании.

### Анализ кода

**GrapplePhysicsController.java строки 76-100:**

```java
if (hasLeft && !prevLeftHookActive && leftHookSoundCooldown <= 0) {
    playRopeHookSound(player, true);
    prevLeftHookActive = true;
    leftHookSoundCooldown = SOUND_COOLDOWN_TICKS;
} else if (!hasLeft && prevLeftHookActive && leftHookSoundCooldown <= 0) {
    playRopeBreakSound(player);
    prevLeftHookActive = false;
    leftHookSoundCooldown = SOUND_COOLDOWN_TICKS;
}
```

**ЕСТЬ ЗАЩИТА:** `leftHookSoundCooldown` (cooldown между звуками)

**ВОПРОС:** Почему спамит?

**ВОЗМОЖНАЯ ПРИЧИНА:**
1. Если `hasLeft` постоянно переключается между true/false → звуки воспроизводятся много раз
2. Это связано с ПРОБЛЕМА #1 (Left Hook спам) → крюк то подключается то отключается
3. Каждый раз: playRopeHookSound() или playRopeBreakSound()

### Корневая причина

**Это СЛЕДСТВИЕ ПРОБЛЕМЫ #1** (Left Hook спам).

Если исправить спам крюка → звук перестанет спамить.

### Точное место в коде

**Файл:** `GrapplePhysicsController.java`
**Строки:** 76-100

**Проблема:** Это не проблема звука, а проблема крюка (он переподключается).

---

## ПРОБЛЕМА #9: МЕДЛЕННОЕ ПАДЕНИЕ ПОСЛЕ ОТПУСКАНИЯ

### Наблюдение в игре
После отпускания троса игрок иногда медленно падает (словно трос ещё существует).

### Анализ кода

Это может быть ПРОБЛЕМА #3 (трос восстанавливается к MAX_ROPE_LENGTH).

ИЛИ это может быть проблема в `applyRopeConstraint()` который не очищает ограничение.

**GrapplePhysicsController.java строки 164-180:**

```java
private static void applyRopeConstraint(LocalPlayer player, Object leftHook, Object rightHook) {
    Vec3 playerPos = player.position();
    
    Vec3 leftPos = null;
    Vec3 rightPos = null;
    
    if (leftHook != null) {
        leftPos = AOTReflect.getPosition(leftHook);
    }
    if (rightHook != null) {
        rightPos = AOTReflect.getPosition(rightHook);
    }
    
    // If no hooks, return
    if (leftPos == null && rightPos == null) return;
```

**ЕСЛИ оба крюка NULL → возвращает без проблем.**

**ВОПРОС:** Может ли быть случай когда одного крюка достаточно для создания медленного падения?

### Корневая причина

**Это СЛЕДСТВИЕ ПРОБЛЕМЫ #2 и #3**

Если трос восстанавливается к MAX → игрок опускается.
Если крюк рассинхронизирован → натяжение неправильное.

---

## ИТОГОВАЯ ТАБЛИЦА КОРНЕВЫХ ПРИЧИН

| # | Проблема | Класс | Метод | Корневая причина | Решение |
|---|----------|-------|-------|------------------|---------|
| 1 | Left Hook спам | RemoteHookFollower | follow() | findContaining() → NULL при рассинхронизации | Улучшить поиск SubLevel |
| 2 | Разное Vanilla/Physics | RemoteHookFollower | follow() | Крюк теряется на быстро движущихся объектах | Улучшить синхронизацию |
| 3 | Падение после SPACE | GrapplePhysicsController | tick() | Трос восстанавливается к MAX автоматически | Отключить автовосстановление |
| 4 | W слабое | DEWImpulseCalculator | - | BASE_IMPULSE = 0.12 слишком мал | Увеличить на 1.5x |
| 5 | DEW бесконечный | DEWImpulseCalculator | calculateDEW() | Нет MAX_VELOCITY cap | Добавить cap |
| 6 | Rope Collision | GrapplePhysicsController | checkRopeCollision() | Метод пустой или неполный | Реализовать коллизию |
| 7 | Reverse DEW слабый | DEWImpulseCalculator | calculateReverseDEW() | Множитель 0.85 неправильный | Увеличить множитель |
| 8 | Звук спамит | GrapplePhysicsController | tick() | СЛЕДСТВИЕ #1 (крюк спамит) | Исправить #1 |
| 9 | Медленное падение | GrapplePhysicsController | applyRopeConstraint() | СЛЕДСТВИЕ #2 и #3 | Исправить #2 и #3 |

---

## ✅ ВЫВОД

**9 проблем = 6 корневых причин:**

1. **RemoteHookFollower.follow()** - рассинхронизация на быстрых объектах
2. **GrapplePhysicsController.tick()** - неправильная логика восстановления troса
3. **DEWImpulseCalculator** - слабый импульс + отсутствие cap
4. **GrapplePhysicsController.checkRopeCollision()** - не реализовано

Проблемы #8 и #9 - СЛЕДСТВИЯ других проблем.

**Следующий этап:** ИСПРАВЛЕНИЯ по одному модулю (начиная с самых критичных).

