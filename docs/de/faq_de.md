# Häufig gestellte Fragen


## Was ist KPM-Manager?
KPM-Manager ist eine Root-Lösung, ähnlich wie Magisk oder KernelSU, welche das Beste beider vereint.
Es kombiniert Magisks bequeme und leichte Installationsmethode über `boot.img` mit KernelSUs starken Kernel-Korrektur-Fähigkeiten.


## Was ist der Unterschied zwischen KPM-Manager und Magisk?
- Magisk modifiziert das Init-System mit einer Korrektur in Ihrem boot image ramdisk, während KPM-Manager den Kernel direkt korrigiert.


## KPM-Manager gegen KernelSU
- KernelSU benötigt den Quellcode für dein Geräte-Kernel, welcher nicht immer von der OEM gegeben wird. KPM-Manager funktioniert allein mit Ihrem Standard-`boot.img`.


## KPM-Manager gegen Magisk, KernelSU
- KPM-Manager erlaubt Ihnen, optional, nicht SELinux zu modifizieren, was bedeutet, dass ein App-Kontext gerootet werden kann, sodass libsu und IPC nicht benötigt werden.
- **Kernel Korrektur Modul** gegeben.


## Was ist ein Kernel Korrigtur Modul?
Etwas Code läuft im Kernelbereich, ähnlich zu Ladbaren Kernel-Modulen (LKM).

Dazu stellt KPM die Fähigkeit bereit, "inline-hook", "syscall-table-hook" im Kernelbereich zu machen.

Für mehr Informationen, siehe [Wie schreibt man ein KPM](https://github.com/yervant7/KPM-Manager/blob/main/doc/module.md)


## Beziehung zwischen KPM-Manager und KernelPatch

KPM-Manager benötigt KernelPatch, übernimmt all seine Fähigkeiten und wurde erweitert.

Sie können nur KernelPatch installieren, aber dies wird Ihnen nicht erlauben, Magisk-Module zu nutzen.

[Lerne mehr über KernelPatch](https://github.com/yervant7/KPM-Manager)


## Was ist SuperKey?
KernelPatch fügt einen neuen System-Abruf (syscall) hinzu, um alle Möglichkeiten, Apps und anderen Programmen im Benutzerbereich, bereitzustellen, dieser System-Abruf ist bekannt als **SuperCall**.
Wenn eine App / ein Programm versucht, **SuperCall** abzurufen, muss es ein Berechtigungsnachweis vorlegen, bekannt als **SuperKey**.
**SuperCall** kann nur erfolgreich abgerufen werden, wenn der **SuperKey** korrekt ist und wenn nicht, dann bleibt der Rufer unbetroffen.


## Was ist mit SELinux?
- KernelPatch bearbeitet den SELinux-Kontext nicht und umgeht den SELinux über einen Haken.
  Dies erlaubt Ihnen, einen Android-Kontext in dem App-Kontext, ohne dem Gebrauch, libsu zu nutzen, um einen neuen Prozess zu starten und darauf IPC auszuführen, zu rooten.
  Dies ist bequem.
- Dazu verwendet KPM-Manager direkt das Magiskkonzept, um weitere SELinux-Unterstützung bereitzustellen.
