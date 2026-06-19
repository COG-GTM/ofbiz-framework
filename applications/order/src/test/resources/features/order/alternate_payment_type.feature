Feature: Sales order paid by an alternate payment type
  As the order management system
  I want orders paid by cash-on-delivery to persist the matching payment preference
  So that fulfilment knows how the order will be settled

  Scenario: Customer order is paid cash on delivery
    Given a sales order for customer "DemoCustomer" in store "9000" paying with "EXT_COD"
    When the cart contains:
      | productId | quantity | unitPrice |
      | GZ-2644   | 1        | 38.40     |
    And the order is placed
    Then the order is created successfully
    And the persisted OrderHeader has status "ORDER_CREATED" and type "SALES_ORDER"
    And the order has 1 OrderItem
    And the order has 1 OrderPaymentPreference of type "EXT_COD"
    And the persisted order grand total is "38.40"
