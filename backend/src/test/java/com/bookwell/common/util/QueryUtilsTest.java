package com.bookwell.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueryUtilsTest {

    @Test
    void escapesSpecialLikeWildcards() {
        assertThat(QueryUtils.escapeLike("%")).isEqualTo("!%");
        assertThat(QueryUtils.escapeLike("_")).isEqualTo("!_");
        assertThat(QueryUtils.escapeLike("100% discount_deal!")).isEqualTo("100!% discount!_deal!!");
        assertThat(QueryUtils.escapeLike("  salon  ")).isEqualTo("salon");
        assertThat(QueryUtils.escapeLike(null)).isEmpty();
    }

    @Test
    void truncatesExcessiveLength() {
        String longInput = "a".repeat(150);
        assertThat(QueryUtils.escapeLike(longInput)).hasSize(100);
    }
}
