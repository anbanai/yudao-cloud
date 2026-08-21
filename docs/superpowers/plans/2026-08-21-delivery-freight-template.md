# 运费模板区域匹配与 0 值配置 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复快递运费模板的省市区域匹配和规则优先级，并让后端与后台管理三套 UI 一致支持 0 元运费及 0 门槛。

**Architecture:** 后端在 `DeliveryExpressTemplateConvert` 组合模板配置时沿 `AreaUtils` 地区树查找收货地址的祖先区域，分别选择收费和包邮规则中最具体的匹配项；更具体的收费规则会屏蔽更宽泛的包邮规则，同级规则保留现有满额包邮语义。后台管理保留现有三套重复表单结构，只调整其输入下限和显式校验，避免影响金额单位转换和 API 数据格式。

**Tech Stack:** Java 17, Spring Boot, MapStruct, Jakarta Bean Validation, JUnit 5, Vue 3, TypeScript, Vben UI, Ant Design Vue, Element Plus, Antdv Next, Vitest.

---

### Task 1: Add failing backend matching tests

**Files:**
- Create: `yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/convert/delivery/DeliveryExpressTemplateConvertTest.java`

- [ ] **Step 1: Write the failing conversion tests**

  Use `DeliveryExpressTemplateConvert.INSTANCE.convertMap` with real area IDs from `area.csv`: `650000` (新疆省级), `650102` (新疆天山区末级), `1` (中国), and `110105` (北京朝阳区). Cover these cases:

  ```java
  @Test
  void testConvertMap_provinceChargeMatchesDistrictAndOverridesCountryFree() {
      DeliveryExpressTemplateDO template = template(1L);
      DeliveryExpressTemplateChargeDO xinjiangCharge = charge(1L, List.of(650000), 1200);
      DeliveryExpressTemplateFreeDO countryFree = free(1L, List.of(1), 0, 0);

      DeliveryExpressTemplateRespBO result =
              DeliveryExpressTemplateConvert.INSTANCE
                      .convertMap(650102, List.of(template), List.of(xinjiangCharge), List.of(countryFree))
                      .get(1L);

      assertThat(result.getCharge().getStartPrice()).isEqualTo(1200);
      assertThat(result.getFree()).isNull();
  }

  @Test
  void testConvertMap_countryFreeMatchesOtherDistrict() {
      DeliveryExpressTemplateRespBO result = convert(110105,
              charge(1L, List.of(650000), 1200), free(1L, List.of(1), 0, 0));

      assertThat(result.getCharge()).isNull();
      assertThat(result.getFree().getFreePrice()).isZero();
      assertThat(result.getFree().getFreeCount()).isZero();
  }

  @Test
  void testConvertMap_sameSpecificityKeepsChargeAndFreeThreshold() {
      DeliveryExpressTemplateRespBO result = convert(650102,
              charge(1L, List.of(650102), 1200), free(1L, List.of(650102), 5000, 2));

      assertThat(result.getCharge()).isNotNull();
      assertThat(result.getFree().getFreePrice()).isEqualTo(5000);
  }

  @Test
  void testConvertMap_withoutMatchingAreaDoesNotReturnTemplate() {
      assertThat(DeliveryExpressTemplateConvert.INSTANCE
              .convertMap(110105, List.of(template(1L)),
                      List.of(charge(1L, List.of(650000), 1200)), List.of()))
              .isEmpty();
  }
  ```

  Keep factory methods in the test class so every rule explicitly sets `templateId`, `areaIds`, `startCount`, `extraCount`, and price fields.

- [ ] **Step 2: Run only the new tests and confirm the expected RED state**

  Run:

  ```bash
  mvn -pl yudao-module-mall/yudao-module-trade-server -am -Dtest=DeliveryExpressTemplateConvertTest test
  ```

  Expected: the province and priority tests fail because the current converter only checks `areaIds.contains(areaId)`, while the exact no-match test remains green.

### Task 2: Add failing backend validation tests

**Files:**
- Create: `yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/controller/admin/delivery/vo/expresstemplate/DeliveryExpressTemplateValidationTest.java`

- [ ] **Step 1: Write Bean Validation tests for non-negative values**

  Build a default Jakarta `Validator` and assert that a charge VO with `startPrice = 0` and `extraPrice = 0`, and a free VO with `freePrice = 0` and `freeCount = 0`, has no violations. Add negative-value cases for all four price/threshold fields and zero/negative cases for `startCount` and `extraCount`.

  The negative cases must assert the field path and validation message, for example:

  ```java
  assertThat(validate(charge.setStartPrice(-1))).anyMatch(v ->
          v.getPropertyPath().toString().equals("startPrice"));
  assertThat(validate(charge.setStartCount(0D))).anyMatch(v ->
          v.getPropertyPath().toString().equals("startCount"));
  ```

- [ ] **Step 2: Run the validation test and confirm RED**

  Run:

  ```bash
  mvn -pl yudao-module-mall/yudao-module-trade-server -am -Dtest=DeliveryExpressTemplateValidationTest test
  ```

  Expected: zero acceptance is already green, while negative and zero quantity assertions fail because the request VO currently has only `@NotNull` constraints.

### Task 3: Implement backend area matching and validation

**Files:**
- Modify: `yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/convert/delivery/DeliveryExpressTemplateConvert.java`
- Modify: `yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/controller/admin/delivery/vo/expresstemplate/DeliveryExpressTemplateChargeBaseVO.java`
- Modify: `yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/controller/admin/delivery/vo/expresstemplate/DeliveryExpressTemplateFreeBaseVO.java`

- [ ] **Step 1: Add request constraints**

  Import `Positive` and `PositiveOrZero`. Annotate `startCount` and `extraCount` with `@Positive`; annotate `startPrice`, `extraPrice`, `freePrice`, and `freeCount` with `@PositiveOrZero`. Keep `@NotNull` on every field so a missing value is still rejected.

- [ ] **Step 2: Replace exact-only matching with ancestor matching**

  In `DeliveryExpressTemplateConvert.convertMap`, add a helper that walks `AreaUtils.getArea(areaId).getParent()` and returns the first matching configured ID's distance from the address. Select the candidate with the smallest distance for charges and frees separately. If the configured ID is the exact address, its distance is 0; the province and country matches have larger distances.

- [ ] **Step 3: Apply cross-type specificity**

  Keep both selected rules when their distances are equal, because this is the existing “same area, threshold-based free shipping” behavior. When the selected charge distance is smaller than the selected free distance, set the free rule to `null` before creating the response BO, so a specific Xinjiang charge cannot be masked by a country/province-wide free rule. Preserve the current behavior when only one type matches.

- [ ] **Step 4: Run the backend tests and make them GREEN**

  Run both test classes:

  ```bash
  mvn -pl yudao-module-mall/yudao-module-trade-server -am \
    -Dtest=DeliveryExpressTemplateConvertTest,DeliveryExpressTemplateValidationTest test
  ```

  Expected: all matching and validation assertions pass with no unrelated test failures.

### Task 4: Add failing frontend validation coverage

**Files:**
- Create: `/Users/medivh/WORKSPACE/yudao-ui-admin-vben/packages/@core/base/shared/src/utils/__tests__/delivery-express-template-validation.test.ts`
- Create: `/Users/medivh/WORKSPACE/yudao-ui-admin-vben/packages/@core/base/shared/src/utils/delivery-express-template-validation.ts`
- Modify: `/Users/medivh/WORKSPACE/yudao-ui-admin-vben/packages/@core/base/shared/src/utils/index.ts`

- [ ] **Step 1: Extract a small shared numeric predicate**

  Define and export `isPositiveNumber(value: unknown): value is number` and `isNonNegativeNumber(value: unknown): value is number`. The predicates must reject `undefined`, `null`, `NaN`, and numeric strings; the first accepts only values greater than 0, and the second accepts 0 and greater.

- [ ] **Step 2: Write the tests before wiring the forms**

  Assert positive quantities reject 0 and negative values, non-negative prices accept 0, and all predicates reject missing/NaN inputs. Run:

  ```bash
  cd /Users/medivh/WORKSPACE/yudao-ui-admin-vben
  pnpm exec vitest run packages/@core/base/shared/src/utils/__tests__/delivery-express-template-validation.test.ts
  ```

  Expected: the new test fails until the predicate module is implemented, then passes after the minimal implementation.

### Task 5: Update all three admin form variants

**Files:**
- Modify: `/Users/medivh/WORKSPACE/yudao-ui-admin-vben/apps/web-antd/src/views/mall/trade/delivery/expressTemplate/modules/charge-item-form.vue`
- Modify: `/Users/medivh/WORKSPACE/yudao-ui-admin-vben/apps/web-antd/src/views/mall/trade/delivery/expressTemplate/modules/free-item-form.vue`
- Modify: `/Users/medivh/WORKSPACE/yudao-ui-admin-vben/apps/web-ele/src/views/mall/trade/delivery/expressTemplate/modules/charge-item-form.vue`
- Modify: `/Users/medivh/WORKSPACE/yudao-ui-admin-vben/apps/web-ele/src/views/mall/trade/delivery/expressTemplate/modules/free-item-form.vue`
- Modify: `/Users/medivh/WORKSPACE/yudao-ui-admin-vben/apps/web-antdv-next/src/views/mall/trade/delivery/expressTemplate/modules/charge-item-form.vue`
- Modify: `/Users/medivh/WORKSPACE/yudao-ui-admin-vben/apps/web-antdv-next/src/views/mall/trade/delivery/expressTemplate/modules/free-item-form.vue`

- [ ] **Step 1: Wire the shared predicates into charge validation**

  Replace truthiness checks with explicit predicates: `startCount` and `extraCount` use `isPositiveNumber`, while `startPrice` and `extraPrice` use `isNonNegativeNumber`. This allows 0 while still rejecting empty and negative values.

- [ ] **Step 2: Wire the shared predicates into free validation**

  Use `isNonNegativeNumber` for both `freeCount` and `freePrice`, because `freeCount = 0` is valid for every charge mode and means that the quantity condition has no threshold. Change each free-count `InputNumber`/`ElInputNumber` `:min` from `1` to `0`.

- [ ] **Step 3: Add concise UI guidance for zero thresholds**

  Update the free-count and free-price column titles or input placeholders to state that 0 means no threshold, without changing API units or the existing yuan/fen conversion.

- [ ] **Step 4: Run the shared test and type checks**

  Run:

  ```bash
  cd /Users/medivh/WORKSPACE/yudao-ui-admin-vben
  pnpm exec vitest run packages/@core/base/shared/src/utils/__tests__/delivery-express-template-validation.test.ts
  pnpm run check:type --filter=@vben/web-antd --filter=@vben/web-ele --filter=@vben/web-antdv-next
  ```

  Expected: the shared test passes and all three applications type-check without changing unrelated files.

### Task 6: Verify the full behavior and report repository boundaries

**Files:**
- No new production files; inspect the backend and UI diffs.

- [ ] **Step 1: Run the trade module test suite**

  ```bash
  mvn -pl yudao-module-mall/yudao-module-trade-server -am test
  ```

- [ ] **Step 2: Build the three affected admin applications**

  ```bash
  cd /Users/medivh/WORKSPACE/yudao-ui-admin-vben
  pnpm run build:antd
  pnpm run build:ele
  pnpm run build:antdv-next
  ```

- [ ] **Step 3: Inspect both repositories independently**

  Confirm the backend repository diff contains only the design/plan docs and trade-module changes. Confirm the UI repository diff contains only the shared predicate and six freight-template form files, and preserve its existing staged `docs/superpowers/specs/2026-08-21-mall-refund-flow-and-amounts-design.md`, `.superpowers/`, and `docs/superpowers/plans/2026-08-21-logo-assets.md` changes.

- [ ] **Step 4: Summarize verification evidence**

  Report exact test/build commands and exit status, identify any unavailable environment-dependent checks, and list the backend and UI repository paths changed.
