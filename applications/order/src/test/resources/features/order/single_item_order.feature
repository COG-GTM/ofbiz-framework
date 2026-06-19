Feature: Single-item sales order
  As the order management system
  I want a single-item order paid by credit card to be persisted correctly
  So that the customer receives the goods they paid for

  Scenario: Customer buys one product and pays by credit card
    Given a sales order for customer "DemoCustomer" in store "9000" paying with "CREDIT_CARD"
    When the cart contains:
      | productId | quantity | unitPrice |
      | GZ-2644   | 1        | 38.40     |
    And the order is placed
    Then the order is created successfully
    And the persisted OrderHeader has status "ORDER_CREATED" and type "SALES_ORDER"
    And the order has 1 OrderItem
    And each OrderItem has status "ITEM_CREATED"
    And the order has 1 OrderPaymentPreference of type "CREDIT_CARD"
    And the persisted order grand total is "38.40"
    And at least 2 OrderStatus records exist
