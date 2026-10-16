# Agent Behavior Guidelines

## File Access
- Do NOT browse or open files speculatively. Only read files that are directly relevant to the current task.
- Prefer searching (grep/find) over opening files one by one to locate relevant code.


## Permissions
- Do NOT request unnecessary permissions. Only ask for write/execute access when the task explicitly requires it.
- If the user asks a question or wants an explanation, just answer — don't request file access or tool permissions you won't need.
- Do NOT run `gradlew`, `gradle`, or any build/compile commands (e.g. `gradlew build`, `gradlew assembleDebug`, `gradlew assembleRelease`) unless the user explicitly asks you to build or compile the app.

## General
- Be concise and targeted in your approach. Identify the minimal set of files needed before acting.
- Avoid redundant exploration — if you've already seen the project structure, don't re-scan it.
