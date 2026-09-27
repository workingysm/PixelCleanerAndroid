# Pixel Cleaner v1.1-secure (Android)

AI 생성 이미지에서 생기는 미세한 얼룩/점상 노이즈와 투명 배경 바깥쪽의 흐린 픽셀 찌꺼기를 줄이는 **완전 로컬·비생성형** Android 앱입니다. 이미지를 AI로 다시 그리지 않으며 픽셀 가로·세로 크기와 캔버스를 바꾸지 않습니다.

## 조절값

- 노이즈 제거: **0~100**, 기본 **15**
- 빠른 노이즈 프리셋: **약하게 8 / 기본 15 / 강하게 25**
- 샤프닝: **0~100**, 기본 **15**
- 외곽 픽셀 정리: **0~100**, 기본 **35**, ON/OFF 가능
- 출력: **무손실 PNG**
- 크롭/리사이즈/AI 재생성: **없음**

Photoshop Camera Raw의 숫자와 내부 알고리즘은 다르므로 15/15가 수학적으로 동일하지는 않습니다. 기본값은 Camera Raw에서 약한 노이즈 감소 + 약한 샤프닝을 준 것과 비슷한 체감을 목표로 합니다.

## 외곽 픽셀 정리

알파가 매우 낮은 픽셀 가운데 실제 불투명 윤곽에 붙지 않은 고립 픽셀만 완전 투명으로 정리합니다. 정상적인 안티앨리어싱 픽셀이 불투명 윤곽에 연결돼 있으면 최대한 유지합니다.

## v1.1 보안 하드닝

- `INTERNET`, 저장소 전체 접근, 카메라 권한을 요청하지 않음
- 평문 네트워크 통신 차단 및 앱 백업 비활성화
- 광고/분석 SDK, 원격 API, WebView, JNI, 동적 코드 로딩 없음
- Android 문서 선택기로 사용자가 고른 파일에만 접근
- 입력 파일 **128 MiB**, 한 변 **8192 px**, 총 **10 MP** 상한으로 메모리 고갈 입력 방어
- MIME/bounds/실제 디코드 크기를 단계적으로 검증
- 저장 실패 시 미완성 MediaStore 항목 삭제
- Release 빌드에서 R8 축소/최적화/난독화 활성화
- 픽셀 처리 버퍼를 줄여 이전 버전보다 피크 메모리 사용량 감소

자세한 항목은 `SECURITY_CHECKLIST_KO.md`를 확인하세요. 이 체크리스트는 구현 하드닝 기록이며 특정 기관의 공식 보안 인증을 의미하지는 않습니다.

## 로컬 빌드

Android Studio에서 프로젝트를 열고 Android SDK 35를 설치한 뒤 `Build > Generate App Bundles or APKs > Generate APKs`를 사용합니다. Release 배포용 APK는 본인의 비공개 signing key로 서명해야 합니다.

CLI 환경에 Gradle 8.9와 Android SDK 35가 있다면:

```bash
gradle --no-daemon clean assembleDebug assembleRelease
```

설치 가능한 debug APK는 보통 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다. Release는 서명 설정 전에는 unsigned APK일 수 있습니다.

## GitHub Actions

`.github/workflows/build-apk.yml`을 포함했습니다. GitHub 저장소에 올린 뒤 **Actions → Build installable Pixel Cleaner APK → Run workflow**를 실행하면 R8이 적용된 Release APK를 빌드하고, 그 실행에서만 쓰는 임시 4096-bit RSA 키로 서명한 설치 가능한 `PixelCleaner-v1.1-secure.apk`를 artifact로 받을 수 있습니다.

이 CI 서명키는 실행이 끝나면 폐기되므로 **다음 빌드에서 기존 설치본 위에 업데이트 설치할 수 없습니다.** 개인 테스트에는 적합하지만 지속 배포에는 본인이 안전하게 보관하는 고정 release signing key를 사용해야 합니다.

## 사용법

1. **이미지 열기**
2. 노이즈 감소를 0~100에서 조정하거나 8/15/25 프리셋 선택
3. 필요하면 샤프닝과 외곽 픽셀 정리 조정
4. **처리**
5. `누르는 동안 원본 보기`로 비교
6. **PNG 저장** → `Pictures/PixelCleaner/`

## 권장 범위

- AI 생성 이미지의 가벼운 얼룩: 노이즈 **10~20**
- 눈에 띄는 얼룩: **20~30**
- 세부선이 물러 보이면 샤프닝: **15~25**
- 투명 바깥 잔픽셀이 남으면 외곽 정리: **40~55**
- 부드러운 발광/그림자까지 사라지면 외곽 정리를 **15~25**로 낮추거나 끄기

입력 제한은 의도적인 보안/메모리 안정성 정책입니다. 4K UHD(3840×2160)는 약 8.3 MP로 허용됩니다.
