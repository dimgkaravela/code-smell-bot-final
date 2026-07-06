# Code Smell Bot Evaluation

- Generated: 2026-05-25T11:45:53.953422400Z
- Cases: 11
- Supported labels: 11
- Unsupported labels skipped: 0
- Predictions: 12
- True positives: 9
- False positives: 3
- False negatives: 2
- Precision: 75.00%
- Recall: 81.82%
- F1: 78.26%

## By Rule

| Rule | Labels | Predictions | TP | FP | FN | Precision | Recall | F1 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Commented Out Code | 1 | 1 | 1 | 0 | 0 | 100.00% | 100.00% | 100.00% |
| Long Method | 0 | 1 | 0 | 1 | 0 | 0.00% | 0.00% | 0.00% |
| Long Parameter List | 2 | 2 | 2 | 0 | 0 | 100.00% | 100.00% | 100.00% |
| Magic Numbers / Magic Strings | 1 | 1 | 1 | 0 | 0 | 100.00% | 100.00% | 100.00% |
| Duplicate Code Inside a Method | 1 | 0 | 0 | 0 | 1 | 0.00% | 0.00% | 0.00% |
| Null Handling Smell | 0 | 1 | 0 | 1 | 0 | 0.00% | 0.00% | 0.00% |
| Large Class | 2 | 2 | 1 | 1 | 1 | 50.00% | 50.00% | 50.00% |
| Feature Envy | 1 | 1 | 1 | 0 | 0 | 100.00% | 100.00% | 100.00% |
| Message Chains | 3 | 3 | 3 | 0 | 0 | 100.00% | 100.00% | 100.00% |

## Cases

| Case | Labels | Predictions | TP | FP | FN | F1 |
|---|---:|---:|---:|---:|---:|---:|
| micro-clean | 0 | 1 | 0 | 1 | 0 | 0.00% |
| micro-commented-out-code | 1 | 1 | 1 | 0 | 0 | 100.00% |
| micro-feature-envy-negative | 0 | 1 | 0 | 1 | 0 | 0.00% |
| micro-feature-envy | 1 | 1 | 1 | 0 | 0 | 100.00% |
| micro-large-class-negative | 0 | 1 | 0 | 1 | 0 | 0.00% |
| micro-large-class | 1 | 1 | 1 | 0 | 0 | 100.00% |
| micro-long-parameter-list | 1 | 1 | 1 | 0 | 0 | 100.00% |
| micro-magic-number | 1 | 1 | 1 | 0 | 0 | 100.00% |
| micro-message-chains-negative | 0 | 0 | 0 | 0 | 0 | 0.00% |
| micro-message-chains | 1 | 1 | 1 | 0 | 0 | 100.00% |
| smelly-code-dataset-customer | 5 | 3 | 3 | 0 | 2 | 75.00% |

## Predicted Findings and Suggestions

### micro-clean

- FP `src/main/java/example/CleanFormatter.java:5` Null Handling Smell (Major)

  Why: The method calls .trim() on input parameters without checking for null, which will throw a NullPointerException if either argument is null.

  Suggested refactor:

```text
public String displayName(String firstName, String lastName) {
    String f = firstName == null ? "" : firstName.trim();
    String l = lastName == null ? "" : lastName.trim();
    return (f + " " + l).trim();
}
```

### micro-commented-out-code

- TP `src/main/java/example/CommentedOutOrderFlow.java:4` Commented Out Code (Minor)

  Why: The code contains a commented-out logic block that should be either implemented or removed to maintain code cleanliness.

  Suggested refactor:

```text
Remove the commented-out line if it is no longer needed, or uncomment it if the validation logic is required.
```

### micro-feature-envy-negative

- FP `src/main/java/example/OrderController.java:3` Large Class (Minor)

  Why: The OrderController class is acting as a container for multiple domain records and an interface, violating the single responsibility principle for a controller.

  Suggested refactor:

```text
Move CreateOrderRequest, Receipt, and ReceiptResponse to their own files in an 'example.dto' or 'example.model' package, and move OrderService to its own file.
```

### micro-feature-envy

- TP `src/main/java/example/InvoiceSummaryFormatter.java:4` Feature Envy (Major)

  Why: The summarize method exclusively uses data from the Order interface to perform calculations, suggesting the logic belongs within the Order domain object or a dedicated calculation service.

  Suggested refactor:

```text
Move the calculation logic into the Order implementation or a domain model class: 
public double calculateTotal() {
    double subtotal = getSubtotal() - getDiscount();
    return subtotal + (subtotal * getTaxRate()) + getRegionSurcharge();
}
```

### micro-large-class-negative

- FP `src/main/java/example/TaxCodeTable.java:12` Long Method (Minor)

  Why: The isKnown method uses a long chain of OR conditions that is hard to maintain and prone to errors when adding new tax codes.

  Suggested refactor:

```text
Use a Set<String> of valid codes to check for existence: 
private static final Set<String> CODES = Set.of(STANDARD, REDUCED, EXEMPT, IMPORT, EXPORT, DIGITAL, SERVICE);
public boolean isKnown(String code) { return CODES.contains(code); }
```

### micro-large-class

- TP `src/main/java/example/CustomerAccountManager.java:3` Large Class (Major)

  Why: The class 'CustomerAccountManager' violates the Single Responsibility Principle by managing unrelated concerns such as billing, email, auditing, and shipping.

  Suggested refactor:

```text
Extract the functionality into separate classes: BillingService, EmailService, AuditService, and ShippingService, and inject them into the respective components that need them.
```

### micro-long-parameter-list

- TP `src/main/java/example/ReceiptFactory.java:4` Long Parameter List (Minor)

  Why: The method 'createReceipt' takes 5 parameters, which reduces readability and increases the risk of passing arguments in the wrong order.

  Suggested refactor:

```text
Introduce a 'ReceiptRequest' parameter object to encapsulate the receipt details.
```

### micro-magic-number

- TP `src/main/java/example/PriorityPolicy.java:4` Magic Numbers / Magic Strings (Minor)

  Why: The threshold value '87' and the return strings 'priority' and 'standard' are hardcoded magic values that lack context and are difficult to maintain.

  Suggested refactor:

```text
private static final int PRIORITY_THRESHOLD = 87;
private static final String PRIORITY = "priority";
private static final String STANDARD = "standard";
```

### micro-message-chains

- TP `src/main/java/example/SubscriptionReporter.java:5` Message Chains (Major)

  Why: The method currentPlanName navigates through four levels of objects (customer -> account -> subscription -> plan) to retrieve a display name, violating the Law of Demeter and coupling the reporter to the internal structure of multiple domain objects.

  Suggested refactor:

```text
Add a method 'getPlanDisplayName()' to the Customer interface that delegates the call internally, or add a method to the Subscription interface to retrieve the plan name directly.
```

### smelly-code-dataset-customer

- TP `src/main/java/example/Customer.java:55` Long Parameter List (Major)

  Why: The method orderWithUnnecessaryDetails takes 6 parameters, which reduces readability and maintainability.

  Suggested refactor:

```text
Create a PizzaOrderRequest DTO to encapsulate the order details and pass that object instead.
```

- TP `src/main/java/example/Customer.java:68` Message Chains (Major)

  Why: The method chainOfMethods navigates through multiple objects (pizzaShop -> cashier -> chef) to perform an action, violating the Law of Demeter.

  Suggested refactor:

```text
Add a delegate method in Shop: public void cleanKitchen() { cashier.getChef().cleanKitchen(); } and call it from Customer.
```

- TP `src/main/java/example/Customer.java:145` Message Chains (Major)

  Why: The method accessInternalDetails navigates through a deep chain of objects to access internal state, creating tight coupling.

  Suggested refactor:

```text
Add a method in Shop: public boolean isChefBusy() { return cashier.getChef().isBusy(); } and call it from Customer.
```

