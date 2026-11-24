require recipes-demo/images/demo-image-common.inc

SUMMARY = "Orin Nano elevator monitoring bring-up image"
IMAGE_INSTALL:append = " elevator-boot-check python3-pyserial sqlite3 util-linux-findmnt"
