# Licensing

Seira mobile is source-available. You can read, change and run the code.
It is not open source, because the license does not allow a competing product.

## Two licenses

| Code | License | File |
|------|---------|------|
| All code in the commits before commit `2b579d1f34b48690109896aef5fc5826121f83d4` (2026-11-27), and in commits that do not contain that commit | Apache License 2.0 | [`LICENSE-APACHE`](LICENSE-APACHE) |
| Code ported from Seira desktop, as [`NOTICE`](NOTICE) lists it | Apache License 2.0 | [`LICENSE-APACHE`](LICENSE-APACHE) |
| All other changes and new files in commits that contain commit `2b579d1f` | PolyForm Shield License 1.0.0 | [`LICENSE`](LICENSE) |

Before commit `2b579d1f`, the `LICENSE` file of this repository held the Apache License 2.0.
That code, which includes the OpenMausBot companion apps, stays available under Apache 2.0.
The Apache License 2.0 lets a modified version use other terms for its changes.
A file that holds both kinds of code follows both licenses.

Third-party parts, such as WebRTC and the Geist font, keep their own licenses. `NOTICE` lists them.

## What the PolyForm Shield license allows

| You want to | Allowed |
|-------------|---------|
| Install Seira from the App Store or Google Play and use it, also for work | Yes. This license covers the source code, not the store apps. |
| Read the code and learn from it | Yes |
| Build the apps and use them yourself or in your company | Yes |
| Use parts of the code in a product that does not compete with Seira | Yes |
| Send a fix or a feature | Yes. Read [Contributions](#contributions) first. |
| Use the code in a product that competes with Seira, also a free one | No |

`LICENSE` defines "competes".
The `Licensor Line of Business:` lines at its top name Seira products.
If the licensor stops one of these products, you still cannot use the code to compete with it.

To ask for a license with other terms, open an issue in this repository.

## Trademarks

Neither license gives rights to the Seira name, logo or characters as trademarks.
Do not use them to name or brand a fork or another product.

OpenMausBot and MausBot are trademarks of Supamaus Software Private Limited.
Seira is not affiliated with or endorsed by Supamaus Software Private Limited or the OpenMausBot project.

## Contributions

Before a maintainer merges your pull request, agree to the [contributor license agreement](CLA.md).
Tick the CLA box in the pull request description. The `CLA` check fails until you do.
A bot then records your agreement in a comment on the pull request.
