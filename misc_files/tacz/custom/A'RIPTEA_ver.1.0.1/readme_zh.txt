使用方法：
首先，TaCZ附属包主要是configuration类文件，少部分为mod，将在后文提及，而前者为.zip格式，此时既不是整合包，也不是mod，还不是数据包(datapack)，更不是资源/材质包(resourcepack)，不要用TaC的经验套用之。
其次，当附属包未声明需要解压时，请勿解压，对于声明需要解压的，应选择解压到当前文件夹而非解压到xxx文件夹中(通常而言)，其中若有进一步的指导文件，请查阅并遵循之。
最后，附属包在任何版本的TaCZ都能生效，不论版本高低，也不论forge或farbic。

显而易见的，TaCZ附属包需要TaCZ的mod本体才能加载，且TaCZ的mod本体除forge核心外无任何必需前置(此处不讨论farbic分支)，请自行准备适合的Minecraft版本和forge版本，然后将TaCZ本体mod放入其中的mods文件夹(若为PCL2启动器，从PCL2下载该mod到指定版本亦可)启动一次，以便后续操作。

应用TaCZ附属包请遵循以下步骤：在不进行游戏时，打开你的Minecraft版本对应的目录(若为PCL2则可点击版本设置-版本文件夹按钮立即跳转；若为HMCL则可点击左侧游戏版本-浏览-游戏文件夹按钮立即跳转)，找到其中的config/tacz/custom文件夹，将您从此处下载得到的压缩包移入其中，随后启动游戏。
特别地，对于mod形式(.jar格式)的TaCZ附属包，应放至对应版本的mods文件夹中，确保debug模式关闭的情况下，进入存档或指令重载，均可使其有效作用，将会在custom文件夹创建其携带的附属包，随后可选地将该mod删除(目前删除mod不会有任何影响)。

警示：
当使用附属包进行创作并公开发布时，应当在简介或评论区中标明附属包作者名（以@到或相同网站的对应账户资料页为佳）以及原始发布网页，同时不应且不得以其他形式进行分发（包括且不限于重新上传、转存等），这在原始公开分发网页中有被提及或引用，故当您使用附属包时默认遵循这些事项。您已经被正当地再次告知了。
当使用附属包内的资产（包括且不限于模型、贴图等）进行直接使用或修改时，应当首先征求该资产原作者或原始分发者的直接同意或授权，若未经授权即对外公开之，或将严厉追责。我，AstralLin，仅代表我独立个人，支持进行二次创作的行为，但非常不认可也不支持对原创资产未经授权即使用的行为。
特别的，针对手机端，因其固有地缺乏渲染管线，且TaCZ并非为手机端设计，故而不对手机端负任何责任，也不对手机端的使用提供任何帮助。
另外，因TaCZ开发尚不完全，侧翻镜及备用镜无法实现其切换逻辑，故分立之，请有需求的用户在背包中另备其对应版物品以随时改装。

其他：
     TaCZ本体并未设计应用于移动端，因后者缺乏渲染管线。
     TaCZ本体默认Alt+T开启游戏内配置界面，你可以在此处快捷地开关debug模式、设置瞄准按键逻辑、设定是否破坏玻璃、变更屏幕准星(需要将格内删空，便能看到可选配置)等，需要mod：Cloth Config API
     /tacz reload，热重载TaCZ本体及附属包的资产，并更新本体以及mod形式携带的附属包。
     /tacz overwrite false，使得在进入存档和/tacz reload时不再更新包，或许当你需要修改本体包内的文件时会用到。
     或者装Cloth Config API这个mod默认按Alt+T进tacz配置界面把debug模式打开，再或者去本地文件里config/tacz-common.toml里把DefaultPackDebug后面的false改成true，效果相同。
     /tacz overwrite true，前者的逆操作，或许你在本体更新时会用到。
     overwrite为false实际上对应debug为true。

兼容性：
     TaCZ本体已兼容 Player Animator 和 Yes Steve Model，二选一，来为您提供玩家模型动画支持。
     TaCZ本体已兼容 越肩视角重制，提供第三人称屏幕准星支持。
     Leawind的第三人称视角 由第三方提供TaCZ兼容：TaCZ：Leawind's Third Person Compat。
     史诗战斗 由第三方提供TaCZ兼容：Darkster Epic Guns TaCZ + EpicFight。
     神化 由第三方提供TaCZ兼容：Apotheosis Modern Ragnarok: Zero。
     关于 更真实的第一人称模型 的兼容方案可见TaCZ大陆地区公开附属包名录。

TaCZ本体现有附属mod(mod形式附属包除外)：
     Enchanted Arsenal，新添TaCZ所有武器可用的十个附魔，其中有两个为诅咒。
     Bullet Proof Enchant，新添针对TaCZ的特化保护附魔。
     TaC：Zero x Create：Precise Manufacturing，可使TaCZ流水线化生产。
     TACZ: Durability，添加耐久与故障机制。
     TACZ：Npcs，添加多种持枪MOB。
     TaCZ：Chamber Clarity，添加检视时的余弹提示。
     
TaCZ相关mod：
     TACZ：Plus，新添战术动作等。
     TACtical Movement，前者的轻量版。
     Tactical Curios，防弹护具。
     Stims and Stuff，插板背心和激素针。
     
     