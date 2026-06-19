Feature: Multi-item sales order
  As the order management system
  I want an order with several different products to persist one line per product
  So that fulfilment and billing handle every line correctly

  Scenario: Customer buys three different products in one order
    Given a sales order for customer "DemoCustomer" in store "9000" paying with "CREDIT_CARD"
    When the cart contains:
      | productId | quantity | unitPrice |
      | GZ-2644   | 2        | 38.40     |
      | GZ-1000   | 1        | 1.99      |
      | GZ-1004   | 3        | 12.00     |
    And the order is placed
    Then the order is created successfully
    And the persisted OrderHeader has status "ORDER_CREATED" and type "SALES_ORDER"
    And the order has 3 OrderItem
    And each OrderItem has status "ITEM_CREATED"
    And the order has 1 OrderPaymentPreference of type "CREDIT_CARD"
    And the persisted order grand total is "114.79"
