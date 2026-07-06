# Code Smell Bot Evaluation

- Generated: 2026-05-25T11:42:59.129891500Z
- Cases: 11
- Supported labels: 11
- Unsupported labels skipped: 0
- Predictions: 10
- True positives: 8
- False positives: 2
- False negatives: 3
- Precision: 80.00%
- Recall: 72.73%
- F1: 76.19%

## By Rule

| Rule | Labels | Predictions | TP | FP | FN | Precision | Recall | F1 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Commented Out Code | 1 | 1 | 1 | 0 | 0 | 100.00% | 100.00% | 100.00% |
| Long Method | 0 | 1 | 0 | 1 | 0 | 0.00% | 0.00% | 0.00% |
| Long Parameter List | 2 | 2 | 2 | 0 | 0 | 100.00% | 100.00% | 100.00% |
| Magic Numbers / Magic Strings | 1 | 1 | 1 | 0 | 0 | 100.00% | 100.00% | 100.00% |
| Duplicate Code Inside a Method | 1 | 0 | 0 | 0 | 1 | 0.00% | 0.00% | 0.00% |
| Large Class | 2 | 1 | 1 | 0 | 1 | 100.00% | 50.00% | 66.67% |
| Feature Envy | 1 | 1 | 1 | 0 | 0 | 100.00% | 100.00% | 100.00% |
| Message Chains | 3 | 3 | 2 | 1 | 1 | 66.67% | 66.67% | 66.67% |

## Cases

| Case | Labels | Predictions | TP | FP | FN | F1 |
|---|---:|---:|---:|---:|---:|---:|
| micro-clean | 0 | 0 | 0 | 0 | 0 | 0.00% |
| micro-commented-out-code | 1 | 1 | 1 | 0 | 0 | 100.00% |
| micro-feature-envy-negative | 0 | 0 | 0 | 0 | 0 | 0.00% |
| micro-feature-envy | 1 | 1 | 1 | 0 | 0 | 100.00% |
| micro-large-class-negative | 0 | 1 | 0 | 1 | 0 | 0.00% |
| micro-large-class | 1 | 1 | 1 | 0 | 0 | 100.00% |
| micro-long-parameter-list | 1 | 1 | 1 | 0 | 0 | 100.00% |
| micro-magic-number | 1 | 1 | 1 | 0 | 0 | 100.00% |
| micro-message-chains-negative | 0 | 0 | 0 | 0 | 0 | 0.00% |
| micro-message-chains | 1 | 1 | 1 | 0 | 0 | 100.00% |
| smelly-code-dataset-customer | 5 | 3 | 2 | 1 | 3 | 50.00% |

## Predicted Findings and Suggestions

### micro-commented-out-code

- TP `src/main/java/example/CommentedOutOrderFlow.java:5` Commented Out Code (Minor)

  Why: The code contains commented-out logic that should be removed to maintain cleanliness and avoid confusion.

  Suggested refactor:

```text
Remove the commented-out line: // if (!valid) { throw new IllegalStateException("Rejected order"); }
```

### micro-feature-envy

- TP `src/main/java/example/InvoiceSummaryFormatter.java:4` Feature Envy (Minor)

  Why: The summarize method performs all calculations by repeatedly querying the Order object for its internal data, suggesting the calculation logic belongs within the Order domain model.

  Suggested refactor:

```text
Move the calculation logic into the Order interface or its implementation: order.calculateTotal().
```

### micro-large-class-negative

- FP `src/main/java/example/TaxCodeTable.java:12` Long Method (Minor)

  Why: The isKnown method uses a long chain of OR conditions that is hard to maintain and prone to errors when adding new tax codes.

  Suggested refactor:

```text
Use a Set<String> of valid codes to check for existence: private static final Set<String> CODES = Set.of(STANDARD, REDUCED, ...); return CODES.contains(code);
```

### micro-large-class

- TP `src/main/java/example/CustomerAccountManager.java:3` Large Class (Major)

  Why: The class acts as a 'God Object' or 'Manager' that aggregates unrelated responsibilities such as billing, email notification, auditing, and shipping logistics.

  Suggested refactor:

```text
Extract the logic into separate classes: BillingService, EmailService, AuditService, and ShippingService, and inject them into the respective components that need them.
```

### micro-long-parameter-list

- TP `src/main/java/example/ReceiptFactory.java:4` Long Parameter List (Minor)

  Why: The method accepts 5 parameters, which reduces readability and increases the risk of passing arguments in the wrong order.

  Suggested refactor:

```text
Introduce a parameter object (e.g., ReceiptRequest) to encapsulate the receipt details.
```

### micro-magic-number

- TP `src/main/java/example/PriorityPolicy.java:5` Magic Numbers / Magic Strings (Minor)

  Why: The magic number '87' is used without context, making the business rule opaque.

  Suggested refactor:

```text
private static final int HIGH_PRIORITY_THRESHOLD = 87;

public String priorityFor(int score) {
    return score > HIGH_PRIORITY_THRESHOLD ? "priority" : "standard";
}
```

### micro-message-chains

- TP `src/main/java/example/SubscriptionReporter.java:5` Message Chains (Major)

  Why: The method currentPlanName navigates through four levels of objects (customer -> account -> subscription -> plan) to retrieve a single property, creating tight coupling to the internal structure of these objects.

  Suggested refactor:

```text
Add a method 'getPlanDisplayName()' to the Customer interface that delegates the call internally, or add a 'getPlanDisplayName()' method to the Subscription interface.
```

### smelly-code-dataset-customer

- TP `src/main/java/example/Customer.java:53` Long Parameter List (Major)

  Why: The method orderWithUnnecessaryDetails takes 6 parameters, which is excessive and suggests a need for an OrderRequest object.

  Suggested refactor:

```text
Create an OrderRequest class to encapsulate the order details and pass that object instead.
```

- TP `src/main/java/example/Customer.java:145` Message Chains (Major)

  Why: The method accessInternalDetails navigates through a long chain of objects to access internal state, creating tight coupling.

  Suggested refactor:

```text
Add a method to Shop or Cashier to expose the required information directly.
```

- FP `src/main/java/example/Customer.java:71` Message Chains (Major)

  Why: The method chainOfMethods navigates through multiple objects (pizzaShop -> cashier -> chef) to perform an action, violating the Law of Demeter.

  Suggested refactor:

```text
Add a delegate method in Shop: public void cleanKitchen() { cashier.getChef().cleanKitchen(); }
```

