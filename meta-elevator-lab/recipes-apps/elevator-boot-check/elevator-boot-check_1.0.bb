SUMMARY = "Record a boot in SQLite to check the elevator lab image"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"
SRC_URI = "file://boot-check.py file://elevator-boot-check.service"
S = "${WORKDIR}"
inherit systemd
SYSTEMD_SERVICE:${PN} = "elevator-boot-check.service"
RDEPENDS:${PN} = "python3-core python3-sqlite3"

do_install() {
    install -d ${D}${bindir} ${D}${systemd_system_unitdir}
    install -m 0755 ${WORKDIR}/boot-check.py ${D}${bindir}/elevator-boot-check
    install -m 0644 ${WORKDIR}/elevator-boot-check.service ${D}${systemd_system_unitdir}/
    sed -i -e 's|@BINDIR@|${bindir}|g' ${D}${systemd_system_unitdir}/elevator-boot-check.service
}
