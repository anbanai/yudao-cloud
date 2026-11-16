# CMP-106 verification

Workspace-installed tools (not committed): Zulu JDK `1.8.0_472` from Azul and Apache Maven `3.9.11` from Maven Central. The system `/usr/bin/git` requires unavailable Xcode tools; local commits used isomorphic-git against the existing repository/index, with explicit path lists. CMP-85 product fields were excluded.

```sh
source /Users/Shared/teaworthshare/.paperclip-tools/env.sh
cd /Users/Shared/teaworthshare/.paperclip-repositories/yudao-cloud-e0d5a436a842
mvn -Dmaven.repo.local=/Users/Shared/teaworthshare/.cache/m2 \
  -pl yudao-module-member/yudao-module-member-server,yudao-module-mall/yudao-module-promotion-server,yudao-module-mall/yudao-module-trade-server \
  -am -Dflatten.skip=true \
  -Dtest=MemberPointPolicyTest,MemberPointLifecycleTest,TradeCouponStackPolicyTest,TradeCouponPriceCalculatorTest,CouponMarketingIntegrationTest,CouponNewcomerServiceTest,TradeCouponOrderHandlerTest,TradeMemberPointOrderHandlerTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  '-DargLine=-Duser.home=/Users/Shared/teaworthshare/.cache/test-home -Djava.io.tmpdir=/Users/Shared/teaworthshare/.cache/test-tmp' test
```

Mockito uses its subclass maker in these modules because this sandbox forbids runtime agent attachment. External identity/level/product/order services are mocked in H2 service tests; coupon/member mappers, row locking, SQL updates, and Spring transaction rollback are real. No production database was contacted.

Final Maven test output is stored in `docs/evidence/cmp106-tests.log`; compile output is `docs/evidence/cmp106-compile.log`. Counts and any remaining checks are recorded in the task delivery document.

Not run: complete MySQL 8 migration/re-run/rollback (no isolated MySQL instance in the supplied environment); full Feign order/payment/stock integration; real multi-tenant scheduler execution; frontend and miniapp UI confirmation flows (separate CMP-102/CMP-103 scope). H2 execution of the FIFO query does not verify MySQL stored procedure syntax or DDL transaction behavior. Cross-service order creation versus newcomer eligibility requires staging race validation. Existing insufficient-balance behavior for clawing back already-spent awarded points remains unchanged and needs product/integration acceptance before release.
