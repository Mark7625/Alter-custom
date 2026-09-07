


<h1 align="center">
  <img width="333" height="82" alt="Asset 5" src="https://github.com/user-attachments/assets/0a892a2f-9ad6-43f9-affa-2e9a865a1d70" />
</h1>

<p align="center">
  <a href="https://github.com/OpenRune/OpenRune-Server/blob/main/LICENSE"><img alt="License" src="https://img.shields.io/github/license/OpenRune/OpenRune-Server?style=for-the-badge&color=6f42c1"/></a>
  <a href="https://oldschool.runescape.wiki/w/Update:Leagues_V:_Raging_Echos_Rewards_Are_Here"><img alt="Revision 240.2" src="https://img.shields.io/badge/Revision-240.2-blueviolet?style=for-the-badge"/></a>
  <a href="https://trello.com/b/A0LefFDs/later"><img alt="Roadmap" src="https://img.shields.io/badge/Trello-Roadmap-026AA7?style=for-the-badge&logo=trello&logoColor=white"/></a>
  <a href="https://github.com/Mark7625/OpenRune-Server/"><img alt="Lines of Code" src="https://img.shields.io/endpoint?url=https%3A%2F%2Fghloc.vercel.app%2Fapi%2FOpenRune%2FOpenRune-Server%2Fbadge%3Fformat%3Dhuman&style=for-the-badge&color=teal"/></a>
  <a href="https://discord.gg/v2qcXzBCwf">
    <img alt="Discord" src="https://img.shields.io/discord/1445802914156249241?label=Discord&logo=discord&logoColor=white&style=for-the-badge&color=5865F2"/>
  </a>
</p>

<p align="center">OpenRune Server is a modular fork of RSMod/Alter that powers an OSRS-compatible server with a plug-and-play plugin ecosystem focused on extensibility and ease of use.</p>

## 🤔 What is OpenRune Server?
OpenRune Server builds on the foundation laid by [RSMod](https://github.com/rsmod/rsmod) to deliver a flexible, developer-friendly OSRS game server. Its modular architecture lets you ship new gameplay features as standalone plugins without touching core engine code. Server owners with little to no programming experience can rely on contributors to drop prebuilt plugins into the `content` module and have them load automatically at runtime.

## 🚀 Why Choose OpenRune Server?
### 🔧 Modular by design
OpenRune Server loads plugins dynamically, making it simple to extend gameplay, content, or systems while keeping the base server clean.

### 👥 Community-driven
Active maintainers review contributions, publish roadmap updates, and support users through Discord and Trello.

### 📏 OSRS-compatible
OpenRune Server adheres to OSRS protocols, giving you the freedom to connect any compliant client and customize server-side behavior.


<!-- content-progress:start -->

## 📊 Content progress

Skills **17/23** · Bosses **8/169** · Raids **0/4** · Minigames **0/51**

Full breakdown in **[PROGRESS.md](PROGRESS.md)**, including every content module and
the bosses that already have drop tables and only need the encounter writing.

<!-- content-progress:end -->
## 🛠️ Getting Started



1. **Clone the repository**
   - `File → New → Project from Version Control` in IntelliJ, then paste `https://github.com/OpenRune/OpenRune-Server.git`.
   - OpenRune Servernatively, clone via Git CLI and open the project manually.

2. **Install dependencies**
   - Ensure you have [IntelliJ IDEA](https://www.jetbrains.com/idea/download/#section=windows).
   - Set the project SDK to Java 21: `File → Project Structure → SDK`. (The `:tools:osrs-mcp` module is built with Java 17 via Gradle’s JVM toolchain; other modules target Java 21.)
   - Recommended: install the Rsc plugin in root of the project [OpenRune IntelliJ Tools-1.0.zip](https://github.com/OpenRune/OpenRune-Server/blob/main/OpenRune%20IntelliJ%20Tools-1.0.zip) for better entity reference tooling.

   - #### You may need to point the settings file [openRune-intelliJ-tools.toml](https://github.com/OpenRune/OpenRune-Server/blob/main/openRune-intelliJ-tools.toml) like so


   <img width="300" height="300" alt="image" src="https://github.com/user-attachments/assets/7aa46983-1f84-4c08-abf8-2f17bc72f073" />


4. **Gradle bootstrap**
   - Open the Gradle tool window.
   - Run `OpenRune Server → Tasks → installation → install`.
   - When the task completes, run `OpenRune Server → Tasks → application → run`.

5. **Verify startup**
   - A successful boot prints `OpenRune Server Successfully initialized` in the terminal.
   - If you only see `OpenRune Server Loaded up in x ms.` you likely skipped a step.

Screenshots showcasing each step are available in the repo under `Resources/main/docs/resources/ReadMe_OpenRune Server/`.

## 🎮 Client Setup
> [!TIP]
> Use [RSProx](https://github.com/blurite/rsprox/releases) to connect; it is actively maintained by trusted developers and supports the required OSRS protocols.

For Windows:
1. Press `⊞ + R` and enter `%USERPROFILE%`.
2. Locate (or create) the `.rsprox` directory.
3. Create `proxy-targets.yaml` with:

```yaml
config:
  - id: 1
    name: OpenRune Server
    jav_config_url: https://client.blurite.io/jav_local_240.ws
    varp_count: 15000
    revision: 240.2
    modulus: YOUR_MODULUS_KEY_HERE
```

Find the modulus in the project root `.data/client.key`, copy it exactly, and replace `YOUR_MODULUS_KEY_HERE`. If `.rsprox` does not exist, launching RSProx once will create it.
Note: RSprox for Private Servers only works currently on Windows and Linux, NOT MacOS!

> [!WARNING]
> And stay away from client's like Devious, as they have been caught adding Account Stealer into their client.
## 📦 Release builds

CI can produce a self-contained `openrune-server-release.zip` with `server.jar`, `game.yml`, and compiled `.data/`. Pushes to `production` publish automatically; other branches can be built manually from **Actions → Release Server**.

See [.github/docs/RELEASE_CI.md](.github/docs/RELEASE_CI.md) for what the workflow does, how to run it manually, and how to build from `production` vs `main`/feature branches.

## 🗺️ Project Planning
- Public roadmap and task board: [OpenRune Server Trello](https://trello.com/b/A0LefFDs/later).
- Trello write access and contributor listing are reserved for active maintainers—contact Chris via Discord with a short summary of your work if you need access.

## 💬 Bug Reports & Support
- Open an issue on [GitHub](https://github.com/OpenRune/OpenRune-Server/issues) with reproduction details.
- Reach the team directly in the [Discord server](https://discord.gg/HAwN6N8F).

## 🙏 Acknowledgments
- Cache management powered by [OpenRune-FileStore](https://github.com/OpenRune/OpenRune-FileStore).
- Original Base [RsMod2](https://github.com/rsmod/rsmod).

## 💙 Contributors
<a href="https://github.com/OpenRune/OpenRune-Server/graphs/contributors" target="_blank"><img src="https://contrib.rocks/image?repo=OpenRune/OpenRune-Server&columns=18" alt="Avatars of all contributors"></a>
