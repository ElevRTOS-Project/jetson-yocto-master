# Jetson용 Yocto 빌드 기록

[README의 빌드 절차](../README.md)는 처음부터 재현하는 순서입니다. 이 문서는 실제로 마주친 문제, 로그에서 확인한 근거와 검증 결과를 남깁니다.

## RAM 16GB PC에서 빌드 환경 구성

| 확인 사항 | 결정과 근거 |
| --- | --- |
| VirtualBox 부팅 로그에서 Windows 가용 RAM이 약 3.8GB인 시점에 기존 VM은 8GB를 요청했습니다. | 기존 터미널용 VM을 두고, 빌드용 WSL 2 Ubuntu 22.04를 별도로 준비했습니다. |
| WSL의 기존 스왑은 2GiB였습니다. | WSL의 RAM 상한을 8GB로 유지하고 스왑을 8GB로 늘렸습니다. 재시작 후 `free -h`에 RAM 7.8GiB와 스왑 8.0GiB가 표시됐습니다. |
| BitBake 태스크 내부에서도 각 패키지의 `make`가 병렬 작업을 시작할 수 있습니다. | `BB_NUMBER_THREADS`를 `1`로, `PARALLEL_MAKE`와 `PARALLEL_MAKEINST`를 `-j 1`로 설정했습니다. |
| LLVM·GCC처럼 오래 걸리는 작업에서는 BitBake의 태스크 번호가 한동안 같았습니다. | 상세 `log.do_compile`의 갱신 시각과 컴파일러 프로세스를 확인해 정상 진행 여부를 판단했습니다. |

## `zipcloak` 패키징 오류 해결

**증상.** 전체 빌드가 3,190 / 8,702번째 태스크까지 진행한 뒤 `zip:do_package`에서 실패했습니다.

```text
QA Issue: File '/usr/bin/zipcloak' from zip was already stripped ... [already-stripped]
```

**로그 확인.** `log.do_compile`에서 생성한 `zipcloak.o`에 디버그 정보가 있었습니다. `log.do_install`에서는 `make install`이 `zipcloak`을 `-s` 옵션으로 한 번 더 링크했습니다. 이때 디버그 정보가 제거돼 OpenEmbedded의 패키징 검사를 통과하지 못했습니다. [Yocto의 already-stripped 검사](https://docs.yoctoproject.org/scarthgap/ref-manual/qa-checks.html#already-stripped)

**수정.** [`zip_3.0.bbappend`](../meta-elevator-lab/recipes-extended/zip/zip_3.0.bbappend)에서 타깃 빌드에 [Makefile 패치](../meta-elevator-lab/recipes-extended/zip/files/0001-install-the-already-built-zip-programs.patch)를 적용했습니다. 패치는 `install` 타깃이 실행 파일을 다시 만드는 의존성을 제거합니다. 설치 단계는 `do_compile`에서 만들어 둔 바이너리를 복사합니다.

**검증.** `bitbake -c package zip`이 성공했습니다. 패키징 전 설치 트리의 `zipcloak`에서 `readelf -SW`로 `.debug_info`와 `.symtab`도 확인했습니다.

```text
Tasks Summary: Attempted 784 tasks of which 774 didn't need to be rerun and all succeeded.
```

## 전체 이미지 빌드의 현재 상태

2026-11-24 17:58 KST에 전체 빌드가 6,042 / 8,702번째 태스크에서 종료됐습니다. 실패한 작업은 `avahi:do_install`입니다. 설치 중 Avahi의 GObject 인트로스펙션 파일을 만드는 실행 파일이 `libavahi-common.so.3`을 로드하지 못했습니다.

```text
Avahi-0.6: error while loading shared libraries: libavahi-common.so.3: cannot open shared object file: No such file or directory
```

상세 로그는 빌드 디렉터리의 `tmp/work/armv8a-oe4t-linux/avahi/0.8/temp/log.do_install`에서 확인할 수 있습니다. Avahi의 수정 및 재검증, 이미지 생성, Jetson 실물 부팅은 [검증 기록](flash-and-verify.md)에 후속 결과를 남깁니다.
