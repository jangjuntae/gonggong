ALTER TABLE product_rule_version
    ADD CONSTRAINT uk_product_rule_version_product_and_id
        UNIQUE (product_id, rule_version_id);

ALTER TABLE application
    DROP CONSTRAINT fk_application_rule_version;

ALTER TABLE application
    ADD CONSTRAINT fk_application_product_rule_version
        FOREIGN KEY (product_id, rule_version_id)
        REFERENCES product_rule_version (product_id, rule_version_id);
