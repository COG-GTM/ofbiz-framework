Feature: Sales order with a promotional adjustment
  As the order management system
  I want promotional discounts to be persisted as order adjustments
  So that the customer is charged the discounted total

  Scenario: Customer order receives a promotional discount
    Given a sales order for customer "DemoCustomer" in store "9000" paying with "CREDIT_CARD"
    When the cart contains:
      | productId | quantity | unitPrice |
      | GZ-2644   | 1        | 38.40     |
    And a promotion adjustment of "-3.84" is applied
    And the order is placed
    Then the order is created successfully
    And the persisted OrderHeader has status "ORDER_CREATED" and type "SALES_ORDER"
    And the order has 1 OrderItem
    And the order has an OrderAdjustment of type "PROMOTION_ADJUSTMENT"
    And the persisted order grand total is "34.56"
