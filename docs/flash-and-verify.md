# Jetson NVMe 기록 및 검증

대상은 Jetson Orin Nano Developer Kit 8GB에 장착한 NVMe입니다. 기존 Jetson에서 루트 장치가 `/dev/nvme0n1p1`임을 확인했습니다. 첫 기록 전에는 프로젝트 파일과 데이터를 별도 저장공간에 백업하고 복사본을 열어 확인합니다.

## 1. 설치 파일 확인

BitBake가 성공한 뒤 **WSL의 빌드 디렉터리**에서 실행합니다.

```bash
deploy=tmp/deploy/images/jetson-orin-nano-devkit-nvme
ls -lh "$deploy"/elevator-lab-image*.tegraflash.tar.gz
image=$(find "$deploy" -maxdepth 1 -type l -name 'elevator-lab-image*.tegraflash.tar.gz' -print -quit)
test -f "$image" && gzip -t "$image"
```

`gzip -t`가 성공하면 파일의 실체를 복사하고 체크섬을 생성합니다.

```bash
cp -L "$image" "$HOME/elevator-nvme.tegraflash.tar.gz" &&
    (cd "$HOME" && sha256sum elevator-nvme.tegraflash.tar.gz > elevator-nvme.tegraflash.tar.gz.sha256)
```

`.tar.gz`와 `.sha256` 두 파일을 기록용 Ubuntu PC의 홈 디렉터리로 옮깁니다.

## 2. Ubuntu PC에서 기록

USB로 기록하는 단계는 Ubuntu로 직접 부팅한 x86-64 PC에서 진행합니다. Jetson에 NVMe를 장착한 상태에서 데이터 전송용 USB 케이블로 PC와 Jetson의 USB-C 단자를 연결합니다. [OE4T 기록 환경 안내](https://github.com/OE4T/meta-tegra/blob/master/docs/Flashing.md#host-environment-issues)

**기록용 Ubuntu PC**에서 전송한 파일을 확인하고 풉니다.

```bash
cd "$HOME"
sha256sum -c elevator-nvme.tegraflash.tar.gz.sha256
```

체크섬에 `OK`가 표시될 때 진행합니다.

```bash
sudo apt update
sudo apt install device-tree-compiler cpp gdisk udisks2 bmap-tools usbutils
mkdir -p ~/jetson-yocto-flash
cd ~/jetson-yocto-flash
tar -xzf ~/elevator-nvme.tegraflash.tar.gz
ls -l initrd-flash .env.initrd-flash
```

기존 Linux가 실행 중인 **Jetson 터미널**에서 복구 모드로 재부팅합니다. 소프트웨어 방식으로 진입할 수 없다면 NVIDIA 안내의 버튼 헤더 절차를 사용합니다. [NVIDIA Force Recovery Mode 안내](https://docs.nvidia.com/jetson/orin-nano-devkit/user-guide/latest/howto.html#force-recovery-mode)

```bash
sudo reboot --force forced-recovery
```

**기록용 Ubuntu PC**에서 `lsusb -d 0955:`를 실행해 Jetson의 복구 USB 장치가 표시되는지 확인합니다. 백업과 대상 장치를 확인한 뒤 아래 명령을 실행합니다. **기존 Jetson NVMe 파티션을 덮어쓰고 보드 부팅 펌웨어도 기록합니다.**

```bash
cd ~/jetson-yocto-flash
sudo ./initrd-flash
```

전원과 USB를 유지한 채 `Successfully finished at ...`를 기다립니다. 실패 시 화면에 출력된 `Host-side log`의 경로에서 원인을 확인합니다. [빌드에 사용한 initrd-flash 소스](https://github.com/OE4T/meta-tegra/blob/fde6672b056fd69a355fb50f6726b0fd1d718fa2/recipes-bsp/tegra-binaries/tegra-helper-scripts/initrd-flash.sh)

## 3. Jetson에서 확인

새 이미지로 부팅한 **Jetson 콘솔**에서 실행합니다.

```bash
cat /etc/os-release
findmnt -n -o SOURCE --target /
systemctl status elevator-boot-check --no-pager
sqlite3 /srv/elevator-lab/boot.db 'SELECT count(*) FROM boots;'
```

성공 기준은 새 Yocto 배포판 정보, 루트 장치 `/dev/nvme0n1p1`, `elevator-boot-check`의 `active (exited)` 상태, 첫 부팅 레코드 1개입니다. 이 서비스는 부팅당 한 번 부팅 ID를 기본 키로 기록합니다. 재부팅 후 같은 조회 결과가 2로 늘어나면 서비스 자동 실행과 DB 보존을 함께 확인할 수 있습니다.

## 검증 기록

| 항목 | 상태 |
| --- | --- |
| BitBake 전체 이미지 빌드 | 중단 · [Avahi 오류](build-log.md#전체-이미지-빌드의-현재-상태) |
| 설치 파일명·크기·SHA-256 | 미확인 |
| Jetson NVMe 기록 | 미검증 |
| 첫 부팅과 서비스 상태 | 미검증 |
| 재부팅 후 SQLite 레코드 보존 | 미검증 |
