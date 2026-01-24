package ru.rs.jopds.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Сервис для отслеживания состояния процесса сканирования
 */
@Slf4j
@Service
public class ScanStatusService {
    
    private final AtomicBoolean isScanning = new AtomicBoolean(false);
    
    /**
     * Устанавливает флаг сканирования
     */
    public void setScanning(boolean scanning) {
        isScanning.set(scanning);
        log.debug("Статус сканирования установлен: {}", scanning);
    }
    
    /**
     * Проверяет, идет ли процесс сканирования
     */
    public boolean isScanning() {
        return isScanning.get();
    }
}
