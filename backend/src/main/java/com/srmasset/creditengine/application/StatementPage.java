package com.srmasset.creditengine.application;

import java.util.List;

public record StatementPage(List<StatementLine> items, int page, int size, long totalElements) {
}
