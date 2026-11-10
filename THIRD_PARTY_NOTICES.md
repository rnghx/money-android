# Protocol reference and licensing

The read-only Sber interoperability code in `DirectSber.java` and its history
mapping are based on the publicly proposed ZenPlugins Sberbank module:

- https://github.com/zenmoney/ZenPlugins/pull/1222
- Author/fork: xsa-dev/ZenPlugins
- Reference commit: 51ad568234809e4f401995d5f969026423ba64e8
- License: GNU GPL version 3; see LICENSE.

This application source is distributed under GPL-3.0. The reference proposal
was open, not merged, when inspected on 2026-11-09. Upstream claims about tests
and successful banking sessions are not tests of this application. This app
uses installation-local device identity and real model/OS information instead
of the reference's fabricated hardware/location telemetry. This variation has
not been verified against an authenticated live Sber account.

The Russian Trusted Root CA is downloaded from the official source:
https://gu-st.ru/content/lending/russian_trusted_root_ca_pem.crt
and matches the reference certificate byte content. It is scoped to
online.sberbank.ru and subdomains by Android Network Security Configuration.
Hostname and certificate validation remain enabled. No system certificate
store is changed. Only fixed, validated Sber hosts/ports are used.

PDFBox and all file import functionality were removed in version 0.4.0.

OKX integration uses the documented REST v5 read API, not ZenPlugins code:
https://www.okx.com/docs-v5/en/ . Fiat display conversion uses the official
Bank of Russia daily XML: https://www.cbr.ru/development/SXML/ .
No OKX credentials were supplied to the development environment or used in tests.

App mark: Phosphor Icons subtract-fill, MIT, Copyright (c) 2023 Phosphor Icons. License: assets/PHOSPHOR-LICENSE.
