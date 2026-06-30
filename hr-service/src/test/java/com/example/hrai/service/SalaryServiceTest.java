package com.example.hrai.service;

import com.example.hrai.entity.SalaryRecord;
import com.example.hrai.exception.BusinessException;
import com.example.hrai.repository.SalaryRecordRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SalaryServiceTest {

    private final SalaryRecordRepository repository = mock(SalaryRecordRepository.class);
    private final SalaryService salaryService = new SalaryService(repository);

    @Test
    void shouldQuerySalaryDetailByEmployeeAndMonth() {
        when(repository.selectOne(any())).thenReturn(record());

        var response = salaryService.getSalaryDetail("张三", YearMonth.of(2026, 6));

        assertThat(response.employeeName()).isEqualTo("张三");
        assertThat(response.salaryMonth()).isEqualTo("2026-06");
        assertThat(response.grossSalary()).isEqualByComparingTo("4000.00");
        assertThat(response.netSalary()).isEqualByComparingTo("3000.00");
        assertThat(response.totalDeduction()).isEqualByComparingTo("1000.00");
    }

    @Test
    void shouldRejectMissingSalaryRecord() {
        when(repository.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> salaryService.getSalaryDetail("张三", YearMonth.of(2026, 7)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未查询到工资明细");
    }

    private SalaryRecord record() {
        return new SalaryRecord(
                1L,
                "张三",
                "2026-06",
                new BigDecimal("4000.00"),
                new BigDecimal("500.00"),
                new BigDecimal("300.00"),
                new BigDecimal("200.00"),
                BigDecimal.ZERO,
                new BigDecimal("4000.00"),
                new BigDecimal("3000.00"),
                "本月实发较应发少 1000，主要来自考勤扣款、社保、公积金"
        );
    }
}
