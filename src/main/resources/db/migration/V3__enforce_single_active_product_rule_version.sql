CREATE UNIQUE INDEX uk_product_rule_version_one_active_per_product
    ON product_rule_version (product_id)
    WHERE active = TRUE;
