package ru.rs.jopds.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.rs.jopds.model.ScanStatus;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Сервис для отслеживания состояния процесса сканирования
 */
@Slf4j
@Service
public class ScanStatusService {
    
    private final AtomicBoolean isScanning = new AtomicBoolean(false);
    private volatile String lastMessage;
    private volatile String lastError;
    
    /**
     * Начинает новое сканирование
     */
    public boolean tryStartScan() {
        if (!isScanning.compareAndSet(false, true)) {
            return false;
        }
        lastMessage = null;
        lastError = null;
        log.debug("Сканирование запущено");
        return true;
    }
    
    /**
     * Завершает сканирование с успешным результатом
     */
    public void completeSuccess(String message) {
        lastMessage = message;
        lastError = null;
        isScanning.set(false);
        log.debug("Сканирование завершено успешно: {}", message);
    }
    
    /**
     * Завершает сканирование с ошибкой
     */
    public void completeError(String error) {
        lastError = error;
        lastMessage = null;
        isScanning.set(false);
        log.debug("Сканирование завершено с ошибкой: {}", error);
    }
    
    /**
     * Проверяет, идет ли процесс сканирования
     */
    public boolean isScanning() {
        return isScanning.get();
    }
    
    /**
     * Возвращает полный статус сканирования
     */
    public ScanStatus getStatus() {
        return ScanStatus.builder()
                .scanning(isScanning.get())
                .message(lastMessage)
                .error(lastError)
                .build();
    }
}
