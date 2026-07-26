# 🔧 FIX #1: DEW UNBOUNDED ACCELERATION

**ЭТАП 5 - ШАГ 1: ЗАПОЛНЕНИЕ ШАБЛОНА ОБОСНОВАНИЯ**

---

## 🎯 ПРОБЛЕМА

**Описание:** Игрок получает бесконечное ускорение при активации DEW. Нет верхнего лимита скорости.

**Наблюдение:** При удержании double-tap SPACE, игрок ускоряется все быстрее и быстрее, пока не выходит из контроля или не падает с карты.

**Файл баг-репорта:** PHASE4_PROBLEMS_TABLE.md строка #7

---

## 🔍 ГИПОТЕЗА

**Почему это происходит:**

Текущая формула DEW:
```java
strength = BASE_IMPULSE * gasPercentage * ropeMultiplier * speedMultiplier * altitudeBonus;
return tiltedDir.scale(strength);
```

С `speedMultiplier = 1.0 + (velocity.length / 20.0) * 0.4`

**Проблема:** Множитель растет линейно с velocity, но velocity растет экспоненциально!

```
Tick 0: vel = 5    → speedMult = 1.1   → impulse = 0.21 → vel_new = 5.21
Tick 1: vel = 5.21 → speedMult = 1.104 → impulse = 0.211 → vel_new = 5.42
Tick 2: vel = 5.42 → speedMult = 1.108 → impulse = 0.212 → vel_new = 5.63
...
Tick 50: vel = 20+ → speedMult = 1.4+ → impulse > 0.3 → vel растет еще быстрее!
```

**Решение:** Ограничить максимальную скорость (MAX_VELOCITY cap)

---

## 📝 ОБОСНОВАНИЕ ГИПОТЕЗЫ

**На что я опираюсь:**
1. Формула видна в коде (строка 48): `double speedMultiplier = 1.0 + (currentVel.length() / 20.0) * 0.4;`
2. Нет явного ограничения скорости после добавления импульса
3. Комментарий в коде: "Speed multiplier adapts to current velocity"
4. Игровое наблюдение: игрок становится все быстрее

**Доказательства:**
- ✅ Видно в коде
- ✅ Логика приводит к экспоненциальному росту
- ✅ Нет MAX_VELOCITY cap в DEWImpulseCalculator
- ✅ Нет cap в GrapplePhysicsController.tick()

---

## 📋 ФАЙЛЫ К ИЗМЕНЕНИЮ

### Файл 1: `DEWImpulseCalculator.java`

**Что менять:**
- Строка 26: Добавить `MAX_VELOCITY` константу
- Строка 55: После расчета impulse, ограничить результат

**Почему этот файл:**
- Это место где вычисляется impulse
- Лучше добавить cap здесь, чем в KeybindEventListener

**Не менять:**
- ✅ Остальные методы DEWImpulseCalculator
- ✅ calculateReverseDEW() (она уже 85% мощности)
- ✅ GrappleStateManager (только читает состояние)

### Файл 2: `GrapplePhysicsController.java`

**Что менять:**
- Строка 127: После applyRopeConstraint(), добавить cap скорости (если нужно)

**Почему:**
- Ограничение может быть полезно и для других систем (Pull, Rope, gravity)

**Не менять:**
- ✅ Логика управления тросом (SPACE/SHIFT)
- ✅ Звуковые эффекты
- ✅ Проверка коллизий

---

## 🛡️ ПОЧЕМУ ЭТО НЕ ПОВЛИЯЕТ НА ОСТАЛЬНОЕ

### DEWImpulseCalculator изолирован

```java
public static Vec3 calculateDEW(LocalPlayer player) {
    // Вычисляем impulse
    // ← ДОБАВИМ CAP ЗДЕСЬ
    return tiltedDir.scale(strength);
}

// Используется ТОЛЬКО в:
// - KeybindEventListener.onClientTickEnd() (строка 55)
// - KeybindEventListener.onClientTickEnd() (строка 64, Reverse)
```

**Зависимости ВХОДЯЩИЕ:**
- `player` object (читаем позицию, не меняем)
- `GasManager` (читаем процент, не меняем)
- `AOTReflect` (читаем позиции крюков, не меняем)

**Зависимости ИСХОДЯЩИЕ:**
- Возвращает `Vec3 impulse` - добавляется к `player.velocity`

**Вывод:** Добавление cap скорости ТОЛЬКО влияет на вывод DEW, не на другие системы!

### GrapplePhysicsController

Если добавим дополнительный cap скорости здесь - он будет работать для ВСЕХ источников velocity, не только DEW.

**Это БЕЗОПАСНЕЕ:**
- Hook/Rope/DEW/Gravity/Collision - все будут ограничены
- Игрок не может выйти из контроля ни из какого источника

---

## ✅ ПЛАН ПРОВЕРКИ РЕГРЕССИИ

После изменения проверить:

### Основной функционал
- [ ] DEW (double-tap SPACE) работает
- [ ] Скорость ограничена максимумом (не бесконечная)
- [ ] Reverse DEW (double-tap S) работает на 85%
- [ ] Gas расходуется правильно

### Input система
- [ ] Left Hook работает
- [ ] Right Hook работает
- [ ] SPACE работает
- [ ] SHIFT работает
- [ ] W работает (если нужна)

### Rope система
- [ ] Трос натягивается
- [ ] Трос держит игрока
- [ ] Трос отпускается

### Physics
- [ ] Гравитация работает
- [ ] Инерция сохраняется
- [ ] На vanilla блоках ОК
- [ ] На physics объектах ОК

### Camera и Sound
- [ ] Камера следует
- [ ] Звук не спамит

---

## 📐 ТЕХНИЧЕСКИЕ ДЕТАЛИ

### Текущая формула:
```java
double strength = BASE_IMPULSE * gasPercentage * ropeMultiplier * speedMultiplier * altitudeBonus;
return tiltedDir.scale(strength);
```

### Новая логика:
```java
double strength = BASE_IMPULSE * gasPercentage * ropeMultiplier * speedMultiplier * altitudeBonus;
Vec3 impulse = tiltedDir.scale(strength);

// CAP СКОРОСТИ (новое)
Vec3 currentVel = player.getDeltaMovement();
Vec3 newVel = currentVel.add(impulse);
double newSpeed = newVel.length();

if (newSpeed > MAX_VELOCITY) {
    newVel = newVel.normalize().scale(MAX_VELOCITY);
    return newVel.subtract(currentVel);  // Вернуть корректированный impulse
} else {
    return impulse;  // Вернуть оригинальный
}
```

### Параметры:
```java
private static final double MAX_VELOCITY = 2.5;  // blocks per tick
// или 
private static final double MAX_VELOCITY = 3.0;  // если хочешь быстрее
```

---

## 🎮 ОЖИДАЕМЫЙ РЕЗУЛЬТАТ

### ДО:
- Игрок начинает с 5 м/с
- После 50 тиков DEW: 20+ м/с (бесконтрольно)

### ПОСЛЕ:
- Игрок начинает с 5 м/с
- После 50 тиков DEW: 2.5 м/с (ограничено)
- Может постоянно летать на 2.5 м/с (контролируемо)

---

## ⚠️ РИСКИ

**Низкий риск:**
- ✅ Изменение затрагивает только DEW
- ✅ Не меняет Input, Hook, Rope, Physics системы
- ✅ Изолировано в одном методе

**Возможные побочные эффекты:**
- ⚠️ DEW может стать слабее (если cap слишком низкий)
- ⚠️ Может быть сложно подняться высоко (если cap слишком низкий)

**Решение:** Подобрать MAX_VELOCITY = 2.5-3.0 экспериментально

---

## 📊 ЗАВИСИМОСТИ И ВЗАИМОДЕЙСТВИЕ

```
KeybindEventListener.onClientTickEnd()
  ├─ DoubleTapDetector.detectDoubleTapSpace()
  └─ DEWImpulseCalculator.calculateDEW()
      ├─ Читает: player.position, player.velocity, gas, rope status
      ├─ Пишет: Vec3 impulse
      └─ ← CAP СКОРОСТИ ЗДЕСЬ
          └─ Результат добавляется к player.velocity

GrapplePhysicsController.applyRopeConstraint()
  ├─ Читает: playerPos, hookPos, currentRopeLength
  └─ Пишет: velocity коррекция (ограничение расстояния)
```

**Взаимодействие:** 
- DEW добавляет impulse к velocity
- GrapplePhysicsController применяет ограничение расстояния
- Оба работают на velocity, но в разных местах
- **НЕ конфликтуют**

---

## ✅ ЗАКЛЮЧЕНИЕ

**Статус:** ✅ Готово к исправлению

**Уверенность:** 95%

**Сложность:** 🟢 Простое

**Дальше:** Готов к написанию кода
