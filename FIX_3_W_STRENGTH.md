# 🔧 FIX #3: W ACCELERATION STRENGTH TOO WEAK

**Статус:** ✅ КОД ГОТОВ
**Файл:** `DEWImpulseCalculator.java`
**Строка:** 26
**Версия:** v1.3.5

---

## 🎯 ПРОБЛЕМА

**Описание:** W дает минимальное ускорение, почти незаметное в игре

**Текущее значение:** `BASE_IMPULSE = 0.12`

**Максимальная мощность:** 0.12 × 1.0 × 2.3 × 1.2 × 1.1 = 0.36 м/с (очень мало!)

---

## ✅ РЕШЕНИЕ

**Увеличить BASE_IMPULSE с 0.12 до 0.18**

```java
// БЫЛО:
private static final double BASE_IMPULSE = 0.12;

// СТАЛО:
private static final double BASE_IMPULSE = 0.18;  // [FIX v1.3.5: increased impulse strength]
```

**Результат:** 0.18 × 1.0 × 2.3 × 1.2 × 1.1 = 0.54 м/с (ощутимо!)

---

## 🔧 КОД

**Файл:** `src/main/java/com/armorberserk/daotcompat/physics/DEWImpulseCalculator.java`

**Изменение:**
```java
@OnlyIn(Dist.CLIENT)
public class DEWImpulseCalculator {
    
    private static final double BASE_IMPULSE = 0.18;  // [FIX v1.3.5: increased from 0.12]
    private static final double UPWARD_TILT = 15.0 * Math.PI / 180.0;  // 15 degrees up
    private static final double MAX_VELOCITY = 2.8;  // [FIX v1.3.5: prevent unbounded acceleration]
```

---

## ✅ ГОТОВО К ВНЕДРЕНИЮ
