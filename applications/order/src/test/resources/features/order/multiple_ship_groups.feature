Feature: Sales order split across multiple ship groups
  As the order management system
  I want a single order to support more than one ship group
  So that quantities can be shipped separately

  Scenario: Order quantity is split across two ship groups
    Given a sales order for customer "DemoCustomer" in store "9000" paying with "CREDIT_CARD"
    When the cart contains:
      | productId | quantity | unitPrice |
      | GZ-2644   | 4        | 38.40     |
    And the items are split across 2 ship groups
    And the order is placed
    Then the order is created successfully
    And the persisted OrderHeader has status "ORDER_CREATED" and type "SALES_ORDER"
    And the order has 1 OrderItem
    And the order has 2 OrderItemShipGroup records
    And the persisted order grand total is "153.60"
