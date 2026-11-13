# Cobble Starter (Fabric 1.21.1 / Cobblemon 1.7.x 서버 전용 모드)

⚠️ 이 zip은 **소스 코드**입니다. 데이터 팩 폴더(datapacks)에 넣으면 아무 반응이 없습니다.
빌드해서 나온 **.jar 파일**을 서버의 **mods 폴더**에 넣어야 합니다.

## 기능
| 기능 | 설명 |
|---|---|
| 첫 접속 스타팅 | 1~9세대 스타팅 27마리(이상해씨~꾸왁스) 중 완전 랜덤, Lv.5, 6V 고정 |
| `/힐` | 내 파티 전체 회복. OP 불필요 |
| `/pb <1~6>` | 해당 칸의 포켓몬을 아이템으로 꺼냄. 우클릭하면 파티로 복귀(파티가 가득 차면 PC) |
| `/starterroll <대상> [wipe]` (`/스타팅`) | OP 전용. 대상에게 랜덤 6V 스타팅 재지급. `wipe true`면 대상의 파티/PC를 먼저 초기화 |
| `/pc` | Cobblemon 기본 명령어. 아래 LuckPerms 설정으로 일반 유저에게 허용 |

첫 접속 기록은 `config/cobblestarter-given.json`에 저장됩니다.

## 빌드 방법 1: GitHub Actions (설치 없이)
1. GitHub에서 새 저장소를 만들고 이 폴더의 파일을 전부 올립니다 (`.github` 폴더 포함).
2. 저장소의 Actions 탭에서 build 작업이 끝나길 기다립니다.
3. 완료된 작업 하단 Artifacts의 `cobble-starter-jar`를 받으면 jar가 들어 있습니다.

## 빌드 방법 2: 내 PC에서
JDK 21과 Gradle 8.10 이상 설치 후, 이 폴더에서 `gradle build` 실행.
결과물: `build/libs/cobble-starter-1.0.0.jar`

## 서버 mods 폴더 (Fabric 1.21.1)
Fabric API, Fabric Language Kotlin, Cobblemon, Create, Easy NPC, WorldEdit, **LuckPerms(Fabric)**, 이 모드의 jar.
(Essential Mod는 클라이언트 쪽 모드라 서버에는 보통 필요 없습니다.)

## LuckPerms 권한 설정 (/pc 허용 + 나머지 명령어 차단)
```
/lp group default permission set cobblemon.command.pc true
```
그 외 일반 유저가 쓸 수 있는 기본 명령어(msg, tell, w, me, list, help, teammsg 등)는
LuckPerms에서 `minecraft.command.<이름>` 노드를 false로 설정해 막습니다.
(노드 이름은 서버에서 `/lp verbose`로 한 번 확인하세요.)

## 테스트 전 확인할 것
- Cobblemon 자체 스타터 선택 화면이 첫 접속 때 같이 뜨는지 확인. 뜬다면 Cobblemon 설정(config/cobblemon)에서 스타터 선택을 끄거나 비워야 합니다.
- 이 코드는 컴파일/실행 검증을 하지 못했습니다. 빌드 오류가 나면 Cobblemon API 호출부(`saveToNBT`, `loadFromNBT`, `translatedName`)의 시그니처를 확인하세요.
