# ЧЗВ


## Какво е KPM-Manager?

KPM-Manager е root решение, подобно на Magisk или KernelSU, което обединява най-доброто от двете.
Комбинира удобния и лесен метод за инсталиране на Magisk чрез boot.img с мощните възможности за пачване на ядрото на KernelSU.

## Каква е разликата между KPM-Manager и Magisk?

Magisk модифицира init системата с пач в ramdisk-а на boot image-а, докато KPM-Manager пачва директно ядрото.


## KPM-Manager срещу KernelSU

KernelSU изисква изходния код на ядрото на устройството, който не винаги се предоставя от производителя. KPM-Manager работи само със стандартния boot.img.


## KPM-Manager срещу Magisk, KernelSU

KPM-Manager позволява по избор да не се модифицира SELinux, което означава, че нишката на приложението може да бъде root-ната, без да е необходимо libsu и IPC.

Kernel Patch Module е включен.


## Какво е Kernel Patch Module?

Някой код се изпълнява в Kernel Space, подобно на Loadable Kernel Modules (LKM).

Освен това, KPM позволява inline-hook и syscall-table-hook в kernel space.

За повече информация виж Как да напишем KPM

## Връзка между KPM-Manager и KernelPatch

KPM-Manager зависи от KernelPatch, наследява всичките му възможности и е разширен.

Можеш да инсталираш само KernelPatch, но това няма да ти позволи да използваш Magisk модули.

Научи повече за KernelPatch

## Какво е SuperKey?

KernelPatch добавя нов системен повик (syscall), който предоставя всички възможности на приложенията и програмите в потребителското пространство. Този syscall се нарича SuperCall.
Когато приложение/програма се опита да извика SuperCall, трябва да предостави идентификационен ключ, наречен SuperKey.
SuperCall може да бъде извикан успешно само ако SuperKey е правилен. Ако не е, извикващият остава незасегнат.

## А какво става със SELinux?

KernelPatch не модифицира SELinux контекста и го заобикаля чрез hook.
Това позволява root достъп до нишка на Android в контекста на самото приложение, без да е необходимо да се използва libsu за стартиране на нов процес и IPC.
Това е много удобно.

Освен това, KPM-Manager използва директно magiskpolicy за допълнителна поддръжка на SELinux.


