# KPM-Manager

The patching of Android kernel focused on KPM.

KPM (Kernel Patch Module): Support for modules that allow you to inject any code into the kernel.

The KPM-Manager is a fork from KernelPatch and APatch to Manage KPM.

## The KPM-Manager It does not provide root access and requires external rooting

## Requirement for patch

CONFIG_KALLSYMS=y  

### Supported Versions

Currently only supports arm64 architecture.  

Linux 3.18 - 6.12 (theoretically)  

## More Information

[Documentation](./doc/)
[Documentations](./docs/)

## Credits

- [vmlinux-to-elf](https://github.com/marin-m/vmlinux-to-elf): Some ideas for parsing kernel symbols.
- [android-inline-hook](https://github.com/bytedance/android-inline-hook): Some code for fixing arm64 inline hook instructions.
- [tlsf](https://github.com/mattconte/tlsf): Memory allocator used for KPM. (Need another to allocate ROX memory.)
- [Magisk](https://github.com/topjohnwu/Magisk): magiskboot for unpack/repack boot.img
- [KernelSU](https://github.com/tiann/KernelSU): Apatch UI startup

### Core

- [KernelPatch](https://github.com/bmax121/KernelPatch/): Made this possible
- [APatch](https://github.com/bmax121/APatch): UI for the Manager

## License

KernelPatch is licensed under the **GNU General Public License (GPL) 2.0** (<https://www.gnu.org/licenses/old-licenses/gpl-2.0.html>).
APatch is licensed under the GNU General Public License v3 [GPL-3](http://www.gnu.org/copyleft/gpl.html).
