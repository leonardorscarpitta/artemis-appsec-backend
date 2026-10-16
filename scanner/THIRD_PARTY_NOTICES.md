# Third-party software in the Artemis scanner runner

The runner image bundles the following programs. Each one is installed **unmodified** from its official release
and executed as a **separate program**; no source code from these projects is part of Artemis. Their license
texts are included in this image under `/usr/share/licenses/<tool>/`.

| Program | Version | License | Source code |
| --- | --- | --- | --- |
| Gitleaks | 8.30.1 | MIT | https://github.com/gitleaks/gitleaks |
| Trivy | 0.76.0 | Apache-2.0 | https://github.com/aquasecurity/trivy |
| Grype | 0.120.1 | Apache-2.0 | https://github.com/anchore/grype |
| Syft | 1.54.1 | Apache-2.0 | https://github.com/anchore/syft |
| Hadolint | 2.15.1 | GPL-3.0 | https://github.com/hadolint/hadolint (corresponding source: tag `v2.15.1`) |
| Semgrep CE (engine only) | 1.181.0 | LGPL-2.1 | https://github.com/semgrep/semgrep — used with Artemis's own rules only, never the Semgrep Registry rules |
| Git | Debian package | GPL-2.0 | https://git-scm.com / Debian source package `git` |

Vulnerability data used by Trivy and Grype comes from public advisory databases (NVD, GitHub Advisory Database,
OSV and others). This product uses data from the NVD API but is not endorsed or certified by the NVD.

Artemis is not affiliated with, sponsored or endorsed by the projects above; their names are trademarks of their
respective owners and are used only to identify the software.
