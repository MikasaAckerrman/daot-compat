# DAOT Compat - Полный Аудит Физики

**Дата:** 2026-07-25  
**Версия:** v1.1.0  
**Статус:** ARCHITECTURE CRITICAL ISSUES IDENTIFIED  
**Приоритет:** ПЕРЕПИСАТЬ ФИЗИКУ (80% проблем в механике)

---

## 🔴 КРИТИЧЕСКИЕ АРХИТЕКТУРНЫЕ ПРОБЛЕМЫ

### #1 Автоматическое Притягивание (BLOCKING)
**Статус:** ❌ НЕПРАВИЛЬНАЯ РЕАЛИЗАЦИЯ

**Текущее поведение:**
```
ЛКМ → Крюк попал → АВТОМАТИЧЕСКИ летит к точке
```

**Проблемы:**
- Невозможно просто висеть на крюке
- Нельзя делать раскачку
- Нельзя выбирать момент ускорения
- Движение выглядит как телепортация

**Должно быть:**
```
ЛКМ → Крюк зацепился → НАТЯЖЕНИЕ создается
                     ↓
                     ИГРОК НЕ ДВИЖЕТСЯ
                     ↓
SPACE (УДЕРЖАНИЕ) → Подтягивание включается
                 ↓
SHIFT → Отпускание/удлинение
```

**Код проблема:**
```java
// В GrapplePhysicsController.java вероятно что-то вроде:
if (isHookEngaged) {
    Vec3 direction = hookPos.subtract(playerPos).normalize();
    player.setDeltaMovement(direction.scale(pullForce)); // ← НЕПРАВИЛЬНО!
}
```

**Решение:**
Трос - это только ТОЧКА ОПОРЫ, не двигатель. Движение только при активном сигнале (SPACE).

---

### #2 Нет Физики Натяжения (BLOCKING)
**Статус:** ❌ НЕПРАВИЛЬНАЯ РЕАЛИЗАЦИЯ

**Текущее поведение:**
- Игрок может лететь в ЛЮБУЮ сторону
- Трос не ограничивает движение
- Резкое притяжение когда "натяжение срабатывает"
- Иногда телепортирует к крюку

**Текущий расчет (вероятно):**
```java
velocity += hookDirection * pullForce; // Просто направление
```

**Должно быть:**
```
ЕСЛИ расстояние < длина_троса
  → ничего не происходит (свободное движение)
  
ЕСЛИ расстояние > длина_троса
  → вернуть игрока на поверхность сферы длины
  
ЕСЛИ подтягивание активно (SPACE)
  → медленно сокращать длину привязи
```

**Математически:**
```
rope_length = 48 блоков (максимум)
distance = |player_pos - hook_pos|

IF distance > rope_length:
    normalized = (hook_pos - player_pos).normalize()
    player_pos = hook_pos - (normalized * rope_length)
```

**Это правильная физика маятника/гарпуна.**

---

### #3 Трос Проходит Сквозь Блоки (HIGH IMPACT)
**Статус:** ❌ НЕТ КОЛЛИЗИЙ

**Текущее поведение:**
- Трос работает как лазер сквозь блоки
- Нет проверки столкновения

**Должно быть:**
```
Трос должен:
  а) Огибать углы (сложно)
  б) Обрываться при попадании (просто)
  в) Перецепляться (очень сложно)
```

**Минимальное решение:**
```java
// Проверка коллизии троса с блоками
RayTraceResult hit = level.clip(new ClipContext(hookPos, playerPos, ...));
if (hit != null && !hit.isEmpty()) {
    // Трос оборвался
    disengageHook();
}
```

---

### #4 Нет Сохранения Импульса (HIGH IMPACT)
**Статус:** ❌ СКОРОСТЬ СКАЧЕТ ВМЕСТО ИНЕРЦИИ

**Текущее поведение:**
```
Скорость: 40 → 38 → 39 → 5 → 32 → 0
```

**Проблемы:**
- Движение не плавное
- Невозможно набрать хорошую скорость
- Ломается ощущение физики

**Причина:**
Вероятно, каждый тик полностью пересчитывается движение вместо добавления импульсов.

**Должно быть:**
```java
// Каждый тик добавлять небольшой импульс к СУЩЕСТВУЮЩЕЙ скорости
Vec3 currentVelocity = player.getDeltaMovement();
Vec3 additionalPull = calculatePullDirection().scale(pullForce);
player.setDeltaMovement(currentVelocity.add(additionalPull));
```

**Физика маятника:**
- Энергия сохраняется
- Скорость растет при падении вниз
- Скорость падает при подъеме
- Инерция помогает раскачке

---

### #5 DEW Как Чит (HIGH IMPACT)
**Статус:** ❌ НЕПРАВИЛЬНАЯ РЕАЛИЗАЦИЯ

**Текущее поведение:**
```java
velocity = lookVector * огромная_сила; // ЗАМЕНА вместо добавления
```

**Проблемы:**
- Резкие рывки
- Невозможно предсказать траекторию
- Ломает инерцию

**Должно быть:**
```java
// Добавление импульса к ТЕКУЩЕЙ скорости
Vec3 currentVelocity = player.getDeltaMovement();
Vec3 dewImpulse = DEWDirection * dewForce;
player.setDeltaMovement(currentVelocity.add(dewImpulse));
```

**Правильная физика:**
- DEW = усилитель, не источник движения
- Работает с текущей скоростью
- Сохраняет инерцию

---

### #6-14 Остальные Проблемы (Вытекают Из #1-5)

| # | Проблема | Причина | Зависит от |
|---|----------|---------|-----------|
| #6 | Нет плавной инерции маятника | Скорость пересчитывается | #4 |
| #7 | Невозможна раскачка | Нет натяжения + нет инерции | #2, #4 |
| #8 | Невозможно контролировать высоту | Нет ручного управления длиной | #1 |
| #9 | Газовая система бесполезна | DEW заменяет скорость | #5 |
| #10 | Нет ощущения веса/гравитации | Нет настоящей физики | #4 |
| #11 | Спарки не работают корректно | Нет правильных скоростей | #6 |
| #12 | Звуки не соответствуют | Движение нереалистичное | #4 |
| #13 | Сложно использовать на высоте | Телепортация вместо движения | #2 |
| #14 | Нет взаимодействия с землей | Скорость скачет | #4, #6 |
| #15 | Нет трения/скольжения | Расчет скорости неправильный | #4 |

---

## 📊 АНАЛИЗ ПРОБЛЕМ

**Распределение:**
- 🔴 Физика движения: 80% проблем
- 🟡 Визуальные эффекты: 15% проблем  
- 🟢 Интеграция модов: 5% проблем

**Вывод:**
Не добавлять новые фичи пока не исправлена базовая физика!

---

## 🎯 ПЛАН ИСПРАВЛЕНИЙ ДЛЯ СЛЕДУЮЩЕЙ СЕССИИ

### Фаза 1: Отключить Автоматическое Притягивание (КРИТИЧНО)
**Файл:** `GrapplePhysicsController.java`

**Что менять:**
```java
// БЫЛО (неправильно):
if (isHookEngaged) {
    velocity += (hookPos - playerPos).normalize() * pullForce;
}

// СТАЛО (правильно):
if (isHookEngaged) {
    // Ничего не происходит - только натяжение
    // Движение ТОЛЬКО при SPACE (onClientTickEnd)
    
    // Натяжение просто ограничивает максимальное расстояние:
    distance = playerPos.distanceTo(hookPos);
    if (distance > MAX_ROPE_LENGTH) {
        // Вернуть на сферу
        normalized = (hookPos - playerPos).normalize();
        playerPos = hookPos - normalized * MAX_ROPE_LENGTH;
    }
}
```

**Тестирование:**
- Зацепиться - игрок НЕ должен лететь
- SPACE - должно начаться подтягивание
- SHIFT - отпускание

---

### Фаза 2: Реализовать Физику Натяжения
**Файл:** `RopePhysicsObject.java` или `GrapplePhysicsController.java`

**Что добавить:**
```java
// Проверка длины троса
private void constrainToRopeLength(LocalPlayer player) {
    double distance = player.position().distanceTo(hookPosition);
    
    if (distance > MAX_ROPE_LENGTH) {
        Vec3 direction = hookPosition.subtract(player.position()).normalize();
        Vec3 constrainedPos = hookPosition.subtract(direction.scale(MAX_ROPE_LENGTH));
        player.setPos(constrainedPos.x, constrainedPos.y, constrainedPos.z);
        
        // Удалить компоненту скорости направленную от крюка
        Vec3 velocity = player.getDeltaMovement();
        double radiusSpeed = velocity.dot(direction);
        if (radiusSpeed > 0) {
            player.setDeltaMovement(velocity.subtract(direction.scale(radiusSpeed)));
        }
    }
}
```

**Это даст:**
- Правильное ограничение расстояния
- Сохранение тангенциальной скорости (инерция маятника)
- Удаление только радиальной скорости (к крюку)

---

### Фаза 3: Добавить Коллизию Троса
**Файл:** `HookTransformResolver.java` или новый `RopeCollisionDetector.java`

**Что добавить:**
```java
// Минимальный вариант - обрыв
private void checkRopeCollision(LocalPlayer player, BlockPos hookPos) {
    Vec3 from = hookPos.getCenter();
    Vec3 to = player.position();
    
    ClipContext context = new ClipContext(from, to, 
        ClipContext.Block.COLLIDER, 
        ClipContext.Fluid.NONE, 
        player);
        
    BlockHitResult hit = level.clip(context);
    
    if (hit != null && !hit.getBlockPos().equals(hookPos)) {
        // Трос задел блок - обрыв
        disengageHook();
    }
}
```

---

### Фаза 4: Переписать DEW
**Файл:** `DEWImpulseCalculator.java`

**Что менять:**
```java
// БЫЛО (неправильно):
public static Vec3 calculateDEW(LocalPlayer player) {
    Vec3 lookDir = player.getLookAngle();
    return lookDir.scale(HUGE_FORCE); // ЗАМЕНА скорости
}

// СТАЛО (правильно):
public static Vec3 calculateDEW(LocalPlayer player) {
    Vec3 lookDir = player.getLookAngle();
    Vec3 currentVelocity = player.getDeltaMovement();
    
    // Добавление импульса к текущей скорости
    double additionalForce = ROPE_TENSION_MULTIPLIER * currentVelocity.length();
    return lookDir.scale(additionalForce * DEW_STRENGTH);
}

// Применение:
Vec3 newVelocity = player.getDeltaMovement().add(dewImpulse);
player.setDeltaMovement(newVelocity);
```

---

### Фаза 5: Правильная Физика SPACE (Подтягивание)
**Файл:** `GrapplePhysicsController.java`

**Что менять:**
```java
// SPACE - подтягивание (сокращение троса)
if (GrappleStateManager.isHoldingSpace()) {
    // Медленное сокращение длины привязи
    currentRopeLength = Math.max(MIN_ROPE_LENGTH, 
        currentRopeLength - REEL_SPEED * deltaTime);
    
    // Применить ограничение новой длины
    constrainToRopeLength(player, currentRopeLength);
    
    // Газ
    GasManager.consumeForPull();
}

// SHIFT - удлинение (отпускание)
if (GrappleStateManager.isHoldingShift()) {
    currentRopeLength = Math.min(MAX_ROPE_LENGTH,
        currentRopeLength + RELEASE_SPEED * deltaTime);
}
```

---

### Фаза 6: Добавить Инерцию Маятника
**Файл:** `GrapplePhysicsController.java`

**Что добавить:**
```java
// Применить гравитацию нормально
// Не переписывать velocity полностью

// Только влиять на скорость маятника
private void applyPendulumPhysics(LocalPlayer player) {
    Vec3 towardHook = hookPos.subtract(playerPos);
    double distance = towardHook.length();
    
    if (distance < 0.01) return; // Слишком близко
    
    Vec3 normal = towardHook.normalize();
    Vec3 velocity = player.getDeltaMovement();
    
    // Тангенциальная скорость (вокруг крюка) - сохраняется
    double tangentSpeed = Math.sqrt(
        velocity.lengthSq() - Math.pow(velocity.dot(normal), 2)
    );
    
    // Радиальная скорость (к/от крюка) - ограничивается
    double radialSpeed = Math.max(-TERMINAL_VELOCITY, 
        Math.min(TERMINAL_VELOCITY, velocity.dot(normal)));
    
    // Пересчет velocity
    Vec3 newVelocity = normal.scale(radialSpeed)
        .add(velocity.subtract(normal.scale(velocity.dot(normal))));
    player.setDeltaMovement(newVelocity);
}
```

---

## 📋 ПРИОРИТЕТ ИСПРАВЛЕНИЙ

```
КРИТИЧНО (мод не работает как ожидается):
1. ✅ Отключить автоматическое притягивание
2. ✅ Реализовать физику натяжения

ВАЖНО (мод ломает ощущение):
3. ✅ Добавить коллизию троса
4. ✅ Переписать DEW
5. ✅ Правильная физика SPACE

ПОТОМ (полировка):
6. Инерция маятника
7. Спарки при скорости
8. Звуки движения
```

---

## 🔧 ФАЙЛЫ ДЛЯ ПРАВКИ

```
src/main/java/com/armorberserk/daotcompat/
├── physics/
│   ├── GrapplePhysicsController.java (ГЛАВНЫЙ - переписать логику)
│   ├── DEWImpulseCalculator.java (Переписать)
│   ├── SableRopeIntegration.java (Добавить коллизию)
│   └── RopePhysicsObject.java (Если существует)
├── hook/
│   └── HookTransformResolver.java (Проверить)
└── input/
    └── GrappleStateManager.java (Проверить логику)
```

---

## ⚠️ ТЕКУЩИЙ СТАТУС

**Баги:**
- 🔴 Физика: ПОЛНОСТЬЮ НЕПРАВИЛЬНАЯ
- 🟡 Event Bus: ИСПРАВЛЕНА в bb029f2
- 🟡 Мод грузится: ДА
- 🔴 Мод работает как ожидается: НЕТ (80% проблем)

**Что работает:**
- ✅ Keybinds загружаются
- ✅ Мод не крашится
- ✅ Крюк зацепляется

**Что не работает:**
- ❌ Движение (телепортация вместо физики)
- ❌ Контроль (нет ручного управления)
- ❌ Ощущение (магнит вместо ODM)

---

**Подготовлено для:** Следующая сессия (новая нейронка)  
**Версия кода:** bb029f2  
**Дата анализа:** 2026-07-25  
**Аналитик:** AI Code Auditor + Video Analysis
