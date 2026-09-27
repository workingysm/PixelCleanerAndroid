# Pixel Cleaner v1.1 보안 하드닝 체크리스트

이 문서는 프로젝트에 실제로 적용한 항목을 기록합니다. 특정 기관의 별도 보안 인증이나 제3자 침투 테스트를 받았다는 의미는 아닙니다.

## 적용됨

- [x] `INTERNET` 권한 없음
- [x] 광범위 저장소/미디어 읽기 권한 없음
- [x] Android 문서 선택기(`ACTION_OPEN_DOCUMENT`)로 사용자가 선택한 단일 이미지에만 접근
- [x] 저장은 Android `MediaStore`를 사용
- [x] 평문 통신 차단 (`usesCleartextTraffic=false` + Network Security Config)
- [x] 앱 백업 비활성화 (`allowBackup=false`, `fullBackupContent=false`)
- [x] 광고/분석/원격 API/동적 코드 로딩/WebView/JNI 없음
- [x] 외부 라이브러리 없는 순수 Java 픽셀 처리
- [x] 입력 MIME·디코드 가능 여부·크기 검증
- [x] 입력 파일 크기 128 MiB 상한
- [x] 한 변 8192 px 및 총 10,000,000 픽셀 상한
- [x] bounds 검사 후 실제 디코드 크기를 다시 검사하여 변경된/비정상 입력 방어
- [x] 메모리 부족을 사용자 오류로 안전하게 처리
- [x] 실패한 `MediaStore` pending 항목을 best-effort 삭제
- [x] UI에 공급자 경로·예외 스택·내부 오류 내용을 노출하지 않음
- [x] Release 빌드 R8 축소/최적화/난독화 활성화
- [x] Launcher Activity 외 별도 exported 컴포넌트 없음
- [x] 이미지 픽셀 크기/캔버스 유지, 알파 채널은 외곽 정리 대상 픽셀 외 유지

## 빌드 후 확인 권장

- [ ] Android Studio Lint / `gradle lintRelease`
- [ ] 실제 Release APK의 매니페스트에서 권한 재확인
- [ ] APK 서명 키를 별도 안전 장소에서 관리하고 저장소에 커밋하지 않기
- [ ] 의존성/Android Gradle Plugin 업데이트 시 재검토
- [ ] 배포 대상 기관이 별도의 모바일 앱 보안 가이드라인을 요구하면 해당 문서 기준으로 추가 점검

## 위협 모델 범위

이 앱은 로컬에서 사용자가 직접 선택한 이미지를 처리하는 단일 목적 도구입니다. 계정, 서버, 로그인, 결제, 웹 콘텐츠, 클라우드 동기화 기능이 없으므로 관련 공격면을 의도적으로 제거했습니다. 이미지 디코딩 자체는 Android 플랫폼의 `BitmapFactory`를 사용하므로 플랫폼 이미지 코덱 보안 업데이트는 기기의 Android 보안 패치 수준에 의존합니다.
