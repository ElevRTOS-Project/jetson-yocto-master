# Jetson Orin Nano용 엘리베이터 관제 Yocto 이미지

Jetson Orin Nano Developer Kit 8GB에서 미니 엘리베이터 관제 소프트웨어를 실행하기 위한 Yocto 이미지입니다. 첫 이미지의 검증 범위는 Jetson 부팅과 로컬 데이터 기록입니다. 이미지에는 UART용 Python 패키지와 SQLite, 부팅할 때마다 SQLite에 부팅 ID를 기록하는 systemd 서비스가 들어 있습니다.

**현재 상태 (2026-11-24)** · `zip` 패키징 수정 검증 완료 · 전체 이미지 빌드는 `avahi:do_install`에서 중단 · Jetson 기록과 부팅 미검증

| 항목 | 사용한 설정 |
| --- | --- |
| 보드 | NVIDIA Jetson Orin Nano Developer Kit 8GB |
| 저장장치 | Jetson NVMe SSD, 기존 루트 파티션 `/dev/nvme0n1p1` |
| Yocto | Scarthgap 5.0 계열, OE4T `tegrademo` |
| BitBake 대상 | `MACHINE=jetson-orin-nano-devkit-nvme`, `elevator-lab-image` |
| 빌드 PC | Windows RAM 16GB, WSL 2 Ubuntu 22.04 LTS x86-64 |

- 빌드 과정과 오류를 추적한 기록은 [빌드 로그](docs/build-log.md)에 있습니다.
- 설치 파일 확인부터 Jetson에 기록하고 재부팅을 검사하는 과정은 [NVMe 기록 및 검증](docs/flash-and-verify.md)에 있습니다.

## WSL 2 준비

Windows PowerShell에서 `wsl -l -v`를 실행해 Ubuntu 22.04가 WSL 2인지 확인합니다. 없다면 `wsl --install -d Ubuntu-22.04`로 설치합니다. 이후 명령은 `wsl -d Ubuntu-22.04`로 연 Ubuntu 터미널에서 일반 사용자로 실행합니다. Scarthgap은 Ubuntu 22.04를 지원하며, Yocto 문서는 WSL 2를 공식 검증 범위 밖의 빌드 환경으로 안내합니다. [Yocto 시스템 요구사항](https://docs.yoctoproject.org/scarthgap/ref-manual/system-requirements.html) · [Yocto WSL 2 안내](https://docs.yoctoproject.org/scarthgap/dev-manual/start.html#setting-up-to-use-windows-subsystem-for-linux-wsl-2)

Windows의 `%USERPROFILE%\.wslconfig`에서 다음 값을 사용했습니다. 기존 `[wsl2]` 구역이 있으면 `memory`와 `swap` 값만 그 구역에 넣고 나머지 키는 보존합니다. 변경 후 WSL 작업을 저장하고 PowerShell에서 `wsl --shutdown`을 실행한 뒤 Ubuntu를 다시 열어 `free -h`로 확인합니다. `memory`는 WSL 2 전체의 RAM 상한이고, 스왑은 메모리가 부족할 때 SSD를 사용하는 공간입니다. [Microsoft WSL 설정](https://learn.microsoft.com/en-us/windows/wsl/wsl-config#configuration-settings-for-wslconfig)

```ini
[wsl2]
memory=8GB
swap=8GB
```

빌드와 캐시는 WSL의 Linux 파일시스템에 저장합니다. 아래의 `~/yocto`를 사용하고, `df -hT "$HOME"`와 Windows 탐색기로 WSL 가상 디스크 및 이를 저장하는 Windows 디스크 양쪽의 여유 공간을 확인합니다.

## 소스 내려받기

Ubuntu 22.04에 빌드 도구를 설치합니다. [Yocto 빌드 호스트 패키지](https://docs.yoctoproject.org/scarthgap/brief-yoctoprojectqs/index.html#build-host-packages)

```bash
sudo apt update
sudo apt install build-essential chrpath cpio debianutils diffstat file gawk \
    gcc git iputils-ping libacl1 liblz4-tool locales python3 python3-git \
    python3-jinja2 python3-pexpect python3-pip python3-subunit socat \
    texinfo unzip wget xz-utils zstd lz4 libssl-dev ca-certificates
sudo locale-gen en_US.UTF-8
export LANG=en_US.UTF-8
```

프로젝트 레이어와 OE4T 소스를 받아 같은 디렉터리에 배치합니다. 최초 구성에 한 번 실행합니다.

```bash
mkdir -p ~/yocto
cd ~/yocto
git clone https://github.com/ElevRTOS-Project/jetson-yocto-master.git
git clone --branch scarthgap --single-branch https://github.com/OE4T/tegra-demo-distro.git
cd ~/yocto/tegra-demo-distro
git checkout --detach 343d89fa7da5a7bd772809375a8f14ef2503a790
git submodule update --init --recursive
git submodule status
```

상위 커밋이 하위 저장소 버전도 고정합니다. 사용한 `meta-tegra` 커밋은 `fde6672b056fd69a355fb50f6726b0fd1d718fa2`, Poky 커밋은 `6b7c1066f778db5a6b3e35400617b10342988fa8`입니다. [고정한 OE4T 소스](https://github.com/OE4T/tegra-demo-distro/tree/343d89fa7da5a7bd772809375a8f14ef2503a790)

## 이미지 빌드

`setup-env`를 실행하면 현재 디렉터리가 OE4T의 `build/`로 바뀝니다. 레이어 추가는 빌드 디렉터리마다 한 번 실행합니다.

```bash
cd ~/yocto/tegra-demo-distro
export LANG=en_US.UTF-8
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
. ./setup-env --machine jetson-orin-nano-devkit-nvme
bitbake-layers add-layer "$HOME/yocto/jetson-yocto-master/meta-elevator-lab"
```

현재 디렉터리의 `conf/local.conf`에 다음 세 값을 지정합니다. 기존 활성 설정이 있으면 해당 줄을 수정합니다. 첫 값은 동시에 실행되는 BitBake 태스크 수, 나머지 두 값은 레시피가 실행하는 `make`의 빌드 및 설치 작업 수입니다. [Yocto 병렬 빌드 변수](https://docs.yoctoproject.org/scarthgap/ref-manual/variables.html#term-BB_NUMBER_THREADS)

```bitbake
BB_NUMBER_THREADS = "1"
PARALLEL_MAKE = "-j 1"
PARALLEL_MAKEINST = "-j 1"
```

```bash
bitbake-layers show-layers
bitbake -e elevator-lab-image | grep -E '^(MACHINE|BB_NUMBER_THREADS|PARALLEL_MAKE|PARALLEL_MAKEINST|IMAGE_FSTYPES)="[^"]*"$'
bitbake elevator-lab-image
```

빌드를 재개할 때는 기존 `build/`를 사용합니다. Ubuntu 터미널을 다시 열었으면 위의 `cd`, `export`, `setup-env` 명령을 실행한 뒤 `bitbake elevator-lab-image`를 실행합니다. BitBake는 유효한 완료 태스크와 다운로드를 재사용합니다.

빌드가 성공하면 `tmp/deploy/images/jetson-orin-nano-devkit-nvme/`에서 `elevator-lab-image*.tegraflash.tar.gz`를 확인합니다. 압축 검사와 기록 명령은 [NVMe 기록 및 검증](docs/flash-and-verify.md)에 있습니다.
