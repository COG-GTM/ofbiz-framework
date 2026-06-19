Feature: Invalid order is rejected
  As the order management system
  I want orders that fail validation to be rejected by storeOrder
  So that no partial or corrupt order is ever persisted

  Scenario: Order with no order lines is rejected
    Given a sales order for customer "DemoCustomer" in store "9000"
    When the storeOrder service is invoked with no order lines
    Then the order creation is rejected with an error
    And no OrderHeader is persisted for the rejected attempt
