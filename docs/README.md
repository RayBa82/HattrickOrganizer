
<div align="left">
  <img src="https://cdn.jsdelivr.net/gh/ho-dev/hattrickOrganizer@master/utils/buildResources/Logo_stable.png" alt="HO logo">
</div>

**Helper Tool for online-manager [Hattrick](http://www.hattrick.org)**
<table>
<tr>
  <td>Latest Release</td>
  <td>
    <a href="https://github.com/ho-dev/HattrickOrganizer/releases/tag/9.3">
    <img src="https://img.shields.io/badge/HO-9.3-brightgreen.svg" alt="latest release" />
    </a>
  </td>
</tr>
<tr>
  <td>Latest Beta Version</td>
  <td>
    <a href="https://github.com/ho-dev/HattrickOrganizer/releases/tag/beta">
    <img src="https://img.shields.io/badge/HO-BETA-blue.svg" alt="latest beta version" />
    </a>
  </td>
</tr>
<tr>
  <td>Latest Development Version</td>
  <td>
    <a href="https://github.com/ho-dev/HattrickOrganizer/releases/tag/dev">
    <img src="https://img.shields.io/badge/HO-DEV-red.svg" alt="latest development version" />
    </a>
  </td>
</tr>
<tr>
  <td>License</td>
  <td>
    <a href="https://github.com/ho-dev/HattrickOrganizer/blob/master/LICENSE">
    <img src="https://img.shields.io/badge/licence-LGPL%203.0-lightgrey.svg" alt="license" />
    </a>
</td>
</tr>
<tr>
  <td>Documentation</td>
  <td>
  <a href='https://ho.readthedocs.io/en/latest/?badge=latest'>
    <img src='https://readthedocs.org/projects/ho/badge/?version=latest' alt='Documentation Status' />
  </a>
</td>
</tr>
<tr>
  <td>Lines of code</td>
  <td>
   <a href='https://github.com/Aaronepower/tokei'>
   <img src='https://tokei.rs/b1/github/ho-dev/hattrickorganizer?category=code' alt='Lines of code' />
  </a>
</td>
</tr>
</table>

-----------------

## Features

<table border="0">
 <tr>
    <td>&bull; Detailed Player development</td>
    <td>&bull; Training Analysis and Prediction</td>
 </tr>
 <tr>
    <td>&bull; Rating Prediction for your lineup</td>
    <td>&bull; Opponents Analysis</td>
 </tr>
 <tr>
    <td>&bull; Tons of statistics</td>
    <td>&bull; Many additional tools and modules</td>
 </tr>
</table>


## Documentation

Documentation for HO! is located [here](https://ho.readthedocs.io/).


## Localization

If you want to contribute or see current translation status please visit [HO translation website](https://poeditor.com/join/project/jCaWGL1JCl).
Please open a request if the language you are interested in is not listed.


## Acknowledgements

Thanks to Ventouris Anastasios (HT-Tasos) for allowing us to use the formulas of his [HT-Tools](https://github.com/ventouris/hattricktools) application

The following companies are kindly supporting HO open source project:

<a href="https://www.jetbrains.com/?from=HO"> <img src="https://user-images.githubusercontent.com/1136496/54837108-e40f7f80-4cc5-11e9-9ea9-047856d17bb4.png" alt="Jetbrains"  width="31" height="34"> </a> [**Jetbrains**](https://www.jetbrains.com/?from=HO) with its full-featured development toolbox

<a href="https://poeditor.com"> <img src="https://user-images.githubusercontent.com/1136496/54937829-795c7f00-4f25-11e9-8394-eb96ddea54fd.png" alt="POEditor" width="34" height="34"> </a> [**POEditor**](https://poeditor.com) with its awesome localization tool

<a href="https://www.syntevo.com/smartgit/"> <img src="https://user-images.githubusercontent.com/1136496/57284884-5d1a3900-70b2-11e9-9917-15a7d2f2c83c.png" alt="SmartGit" width="50" height="34"> </a> [**Syntevo**](https://www.syntevo.com/smartgit/) with its powerful and multi-platform graphical Git client

<a href="https://anvil.works/"> <img src="https://user-images.githubusercontent.com/1136496/74674400-8d4e4680-51b1-11ea-80ea-d04d554a30ba.png" width="34" height="34" alt="Anvil"> </a> [**Anvil**](https://anvil.works/): Full stack web apps
with nothing but Python

<a href="https://www.ej-technologies.com/products/install4j/overview.html/"> <img src="https://user-images.githubusercontent.com/1136496/83647200-f026ea80-a5b4-11ea-80de-5f6cb9583783.png" alt="Intall4j" width="92" height="26"> </a> [**Install4j**](https://www.ej-technologies.com/products/install4j/overview.html/): powerful multi-platform Java installer builder that generates native installers and application launchers for Java applications

## Dependencies

HO bundles a number of libraries that are compatibly licensed.  We list
these here.

* commons-codec
  * License: Apache License, version 2.0
  * For details, see [``docs/Licences/common-codecs/LICENSE.txt``](Licences/common-codecs/LICENSE.txt)

* darklaf
  * License: MIT
  * For details, see [``docs/Licences/darklaf/README``](Licences/darklaf/LICENSE)

* hsqldb
  * License: has its own licence based on BSD
  * For details, see [``docs/Licences/hsqldb/LICENSE.txt``](Licences/hsqldb/LICENSE.txt)

* OkHttp
  * License: Apache License, version 2.0
  * For details, see [``http://www.apache.org/licenses/LICENSE-2.0.txt``](http://www.apache.org/licenses/LICENSE-2.0.txt)

* ospackage
  * License: Apache License, version 2.0
  * For details, see [``docs/Licences/ospackage/LICENSE.txt``](Licences/ospackage/LICENSE.txt)

* scribe
  * License: MIT
  * For details, see [``docs/Licences/scribe/LICENSE.txt``](Licences/scribe/LICENSE.txt)

## Create a local build

```bash
./gradlew createVersion createDistribution
cd build/install/XXX && java -jar HO.jar
```

## Create a macOS .dmg

```bash
./gradlew dmg
```

One call: stamps the version, builds the distribution, renders an icon from
`utils/buildResources/Logo.png` and runs the JDK's `jpackage`. No install4j
licence needed. The result is `build/macapp/HO-10.0.dmg`.

It is unsigned and not notarized, so the first launch on another machine needs
right-click → Open, or:

```bash
xattr -dr com.apple.quarantine /Applications/HO.app
```

To build signed artefacts for all platforms use `./gradlew release` — that path
needs an install4j licence (`INSTALL4J_LICENSE` and `INSTALL4J_HOME`).

### Just the .app, by hand

```bash
./gradlew createVersion createDistribution

jpackage --type app-image --input "$PWD/build/install/XXX" --dest "$PWD/build/macapp" \
  --name HO --main-jar HO.jar --main-class core.HOLauncher --app-version 10.0 \
  --arguments INSTALLED

open build/macapp/HO.app
```

`--input`/`--dest` must be **absolute**, otherwise jpackage reports "main JAR not
present in input directory". `--arguments INSTALLED` makes the bundle write
`db/`, `logs/` and `user.xml` to `~/Library/Application Support/HO` instead of
the launch working directory. Add `--icon "$PWD/build/HO.icns"` for the HO icon
— there is no `.icns` in the repo, `./gradlew jpackageDmg` builds one there.
