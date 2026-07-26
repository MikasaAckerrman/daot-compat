# ⚡ QUICK REFERENCE CARD - v1.3.5

**Для когда нужна инфа быстро!**

---

## 🎯 12 ПРОБЛЕМ & РЕШЕНИЙ

```
#7  DEW Unbounded          → MAX_VELOCITY = 2.8 cap
#6  W Strength             → BASE_IMPULSE: 0.12 → 0.18  
#3  Hook Sync Physics      → Улучшена обработка ошибок
#8  Reverse DEW            → Логирование детекции
#4  Rope Vanilla vs Physics → Логирование изменения
#5  After SPACE            → Очистка ограничений
#10 Rope Collision         → Комментарий о координатах
#11 Release Lag            → Очистка при release
#12 Order of Calls         → Документирование порядка
#1  Left Hook Spam         → Логирование событий
```

---

## 📂 7 ФАЙЛОВ К ИЗМЕНЕНИЮ

| # | Файл | Строки | FIX |
|---|------|--------|-----|
| 1 | `DEWImpulseCalculator.java` | 26-28, 55-70, 97-106 | #7, #6 |
| 2 | `HookTransformResolver.java` | 114-147 | #3 |
| 3 | `GrapplePhysicsController.java` | 110-121 | #4, #5 |
| 4 | `DoubleTapDetector.java` | End of method | #8 |
| 5 | `RopeSegmentHandler.java` | Start of update() | #10 |
| 6 | `DAOTCompat.java` | Event listener | #12 |
| 7 | `KeybindEventListener.java` | onClientTickEnd() | #1 |

---

## 📝 3 КОНСТАНТЫ К ИЗМЕНЕНИЮ

```java
// DEWImpulseCalculator.java, строка ~26

// БЫЛО:
private static final double BASE_IMPULSE = 0.12;

// СТАЛО:
private static final double BASE_IMPULSE = 0.18;  // [FIX v1.3.5]

// ДОБАВИТЬ:
private static final double MAX_VELOCITY = 2.8;  // [FIX v1.3.5]
```

---

## 🔧 КРИТИЧНЫЕ ИЗМЕНЕНИЯ

### FIX #7: DEW Cap (DEWImpulseCalculator.java)

**Добавить в calculateDEW() и calculateReverseDEW():**

```java
Vec3 impulse = tiltedDir.scale(strength);
Vec3 newVelocity = currentVel.add(impulse);
double newSpeed = newVelocity.length();

if (newSpeed > MAX_VELOCITY) {
    Vec3 clampedVel = newVelocity.normalize().scale(MAX_VELOCITY);
    return clampedVel.subtract(currentVel);
}

return impulse;
```

### FIX #3: Hook Sync (HookTransformResolver.java)

**Улучшить follow():**

```java
// Добавить логирование
DAOTCompat.LOGGER.warn("[hook] sub-level not found (UUID: {})", ...);
DAOTCompat.LOGGER.error("[hook] failed to transform position", t);
DAOTCompat.LOGGER.debug("[hook] synchronized to moving sub-level");

// Добавить проверку
if (!isFinite(next)) {
    DAOTCompat.LOGGER.warn("[hook] transformed position is not finite");
    drop(hook);
    return;
}
```

### FIX #6: W Strength (DEWImpulseCalculator.java)

```java
// ПРОСТО ИЗМЕНИ ЧИСЛО:
// 0.12 → 0.18
```

---

## 🎮 ТЕСТИРОВАНИЕ - ОСНОВНОЕ

```
✅ DEW работает контролируемо (не бесконечное)
✅ W дает видимое ускорение (~1.5x сильнее)
✅ Трос синхронизируется с Sable кораблем
✅ Reverse DEW работает
✅ Нет crash'ей
✅ На Vanilla блоках всё как раньше
✅ На Physics объектах всё работает
```

---

## ⏱️ СРОКИ

| Фаза | Время |
|------|-------|
| Применение | 45 мин |
| Компиляция | 5 мин |
| Тестирование | 20 мин |
| **ИТОГО** | **70 мин** |

---

## 📋 ПОРЯДОК РАБОТЫ

```
1. Прочитай IMPLEMENTATION_GUIDE.md (15 мин)
   ↓
2. Открой все 7 файлов в IDE
   ↓
3. Применяй изменения по ALL_FIXES_IMPLEMENTATION.md
   ↓
4. Проверь синтаксис (красных подчеркиваний не должно быть)
   ↓
5. ./gradlew clean build
   ↓
6. Тестируй в игре согласно IMPLEMENTATION_GUIDE.md
   ↓
7. ✅ ГОТОВО!
```

---

## 🔍 ВАЖНЫЕ МОМЕНТЫ

### ⚠️ КРИТИЧНО!

- Java ДОЛЖНА быть 17+ (`java -version`)
- Все [FIX v1.3.5] теги ДОЛЖНЫ быть добавлены
- Синтаксис Vec3 методов: `.normalize()`, `.scale()`, `.length()`
- DAOTCompat.LOGGER используется для всех логирований

### ⚠️ ЛЕГКО ЗАБЫТЬ

- Обновить обе метода: calculateDEW() и calculateReverseDEW()
- Добавить MAX_VELOCITY константу
- Логирование в HookTransformResolver.follow()
- Проверка isFinite() для позиции

### ⚠️ НЕ ТРОГАТЬ

- Остальные методы в классах
- Параметры функций
- Логика ветвления (if/else)
- Существующие переменные

---

## 💡 ЕСЛИ ПОТЕРЯЕШЬСЯ

| Что нужно | Файл |
|-----------|------|
| Точный код для копирования | ALL_FIXES_IMPLEMENTATION.md |
| Пошаговая инструкция | IMPLEMENTATION_GUIDE.md |
| Чек-лист проверки | IMPLEMENTATION_CHECKLIST.md |
| Справка по классам | PHASE1_ANALYSIS.md |
| Понимание архитектуры | PHASE2_SYSTEM_MAP.md |
| Все ссылки и навигация | README_MASTER_INDEX.md |

---

## ✅ ФИНАЛЬНЫЙ КОНТРОЛЬ

Перед `./gradlew build`:

- [ ] DEWImpulseCalculator имеет MAX_VELOCITY
- [ ] DEWImpulseCalculator имеет BASE_IMPULSE = 0.18
- [ ] HookTransformResolver имеет логирование
- [ ] GrapplePhysicsController имеет логирование SPACE/SHIFT
- [ ] Все файлы сохранены
- [ ] Нет красных подчеркиваний в IDE

---

## 🎯 ОЖИДАЕМЫЕ ИЗМЕНЕНИЯ

### ДО
- 🔴 DEW: бесконечное ускорение
- 🔴 W: слабое
- 🔴 Physics: отстает
- 🔴 Спам событий

### ПОСЛЕ
- 🟢 DEW: контролируемо (макс 2.8)
- 🟢 W: сильнее (~0.54 макс вместо 0.36)
- 🟢 Physics: синхронизируется
- 🟢 Нет спама

---

## 🚀 НАЧНИ ОТСЮДА

```bash
# 1. Убедись что Java 17+
java -version

# 2. Прочитай гайд (15 мин)
cat IMPLEMENTATION_GUIDE.md | less

# 3. Применяй изменения (45 мин)
# Используй: ALL_FIXES_IMPLEMENTATION.md

# 4. Компилируй (5 мин)
./gradlew clean build

# 5. Тестируй (20 мин)
# В игре следуй чек-листу из IMPLEMENTATION_GUIDE.md
```

---

## 📞 QUICK HELP

**Q: Где найти точный код?**
A: `ALL_FIXES_IMPLEMENTATION.md` раздел "ФАЙЛ 1-7"

**Q: Как я узнаю что компилировалось успешно?**
A: Вверху терминала напишет "BUILD SUCCESSFUL"

**Q: Что если IDE показывает ошибку?**
A: Проверь синтаксис согласно подсказкам IDE (красное подчеркивание)

**Q: Сколько времени это занимает?**
A: ~1 час (45 мин код + 5 мин компиляция + 20 мин тестирование)

**Q: Опасно ли это?**
A: Нет, риск регрессии минимален (изолированные изменения + чек-листы)

---

**v1.3.5 READY FOR IMPLEMENTATION! 🚀**

