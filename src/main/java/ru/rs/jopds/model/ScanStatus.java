package ru.rs.jopds.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Статус процесса сканирования INPX
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanStatus {

    private boolean scanning;

    private String message;

    private String error;
}
