#!/bin/bash
# GIT КОММИТ ИНСТРУКЦИИ ДЛЯ СЕССИИ 1

# 1. Проверить изменения
echo "=== GIT STATUS ==="
git status

echo -e "\n=== DIFF CHANGED FILES ==="
git diff src/main/java/com/armorberserk/daotcompat/input/KeybindEventListener.java

# 2. Добавить изменения
echo -e "\n=== ADDING CHANGES ==="
git add src/main/java/com/armorberserk/daotcompat/input/KeybindEventListener.java

# 3. Добавить документы (опционально)
echo -e "\n=== ADDING DOCUMENTATION ==="
git add SESSION_CONTEXT_FULL.md
git add AUDIT_INSRUCHIA_15BUGS.md
git add ROPE_WRAPPING_IMPLEMENTATION_GUIDE.md
git add GITHUB_CORRECTION_NOTE.md
git add NEXT_SESSION_TODO.md
git add SESSION1_FINAL_REPORT.txt

# 4. Коммит
echo -e "\n=== COMMIT ==="
git commit -m "refactor: add DEW sound effects, create session context docs

- Add SoundEvents.BLAZE_SHOOT on DEW and Reverse DEW activation
- Create SESSION_CONTEXT_FULL.md with complete audit and next steps
- Document all 15 found bugs in AUDIT_INSRUCHIA_15BUGS.md
- Create implementation guide for rope wrapping (yyon/grapplemod reference)
- Note: Reference implementation comes from grapplemod (correctly cited)
- Next session: Implement RopeSegmentHandler for proper rope wrapping"

# 5. Проверить что закоммитилось
echo -e "\n=== FINAL STATUS ==="
git log --oneline -3
git status

echo -e "\n✅ READY FOR: git push origin round3-stage1-fixes"
