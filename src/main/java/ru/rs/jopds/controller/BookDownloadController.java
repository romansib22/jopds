package ru.rs.jopds.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.rs.jopds.config.InpxProperties;
import ru.rs.jopds.repository.BookRepository;
import ru.rs.jopds.repository.entity.BookEntity;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Контроллер для скачивания книг из архивов
 */
@Slf4j
@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookDownloadController {

    private final InpxProperties inpxProperties;
    private final BookRepository bookRepository;

    /**
     * Скачивает файл книги из архива
     *
     * @param bookId ID книги в БД
     * @return файл для скачивания
     */
    @GetMapping("/download")
    public ResponseEntity<Resource> downloadBook(@RequestParam Long bookId) {

        try {
            // Получаем информацию о книге из БД
            BookEntity bookEntity = bookRepository.findById(bookId)
                    .orElse(null);

            if (bookEntity == null) {
                log.error("Книга с ID {} не найдена в БД", bookId);
                return ResponseEntity.notFound().build();
            }

            String archivePath = bookEntity.getArchivePath();
            String format = bookEntity.getFormat();
            String filePath = bookEntity.getFilePath() + "." + format;

            if (archivePath == null || filePath == null) {
                log.error("Не указан путь к архиву или файлу для книги с ID {}", bookId);
                return ResponseEntity.notFound().build();
            }

            // Определяем полный путь к архиву
            Path archiveFullPath;
            if (inpxProperties.getBookDirectory() != null && !inpxProperties.getBookDirectory().isEmpty()) {
                // Используем отдельную директорию для книг
                Path bookDir = Paths.get(inpxProperties.getBookDirectory());
                archiveFullPath = bookDir.resolve(archivePath);
            } else if (inpxProperties.getFilePath() != null && !inpxProperties.getFilePath().isEmpty()) {
                // Если bookDirectory не указан, используем директорию INPX файла
                Path inpxPath = Paths.get(inpxProperties.getFilePath());
                archiveFullPath = inpxPath.getParent().resolve(archivePath);
            } else {
                // Если не указаны ни bookDirectory, ни filePath, выдаем ошибку
                log.error("Не задан путь к директории с книгами (bookDirectory) или к inpx-файлу (filePath)");
                return ResponseEntity.badRequest().build();
            }

            if (!Files.exists(archiveFullPath)) {
                log.error("Архив не найден: {}", archiveFullPath);
                return ResponseEntity.notFound().build();
            }

            // Открываем архив и извлекаем файл
            byte[] fileContent;
            try (ZipFile zipFile = new ZipFile(archiveFullPath.toFile())) {
                // Ищем файл в архиве (может быть с разными разделителями пути)
                ZipEntry entry = zipFile.getEntry(filePath);
                if (entry == null) {
                    // Пробуем с обратными слешами
                    entry = zipFile.getEntry(filePath.replace("/", "\\"));
                }
                if (entry == null) {
                    // Пробуем с прямыми слешами
                    entry = zipFile.getEntry(filePath.replace("\\", "/"));
                }

                if (entry == null) {
                    log.error("Файл не найден в архиве: {} в {}", filePath, archiveFullPath);
                    return ResponseEntity.notFound().build();
                }

                // Читаем весь файл из архива в память
                try (InputStream inputStream = zipFile.getInputStream(entry)) {
                    fileContent = inputStream.readAllBytes();
                }
            }

            // Создаем ресурс из прочитанных данных
            ByteArrayResource resource = new ByteArrayResource(fileContent);

            // Определяем имя файла для скачивания
            String fileName = bookEntity.getTitle();
            //Если книга из серии, то добавляем номер серии.
            if (bookEntity.getSeries() != null && !bookEntity.getSeries().isEmpty() && bookEntity.getSeriesNumber() != null) {
                String seriesNumber = bookEntity.getSeriesNumber().toString();
                if (seriesNumber.length() == 1) {
                    seriesNumber = "0" + seriesNumber;
                }
                fileName = seriesNumber + "." + fileName;
            }

            // Очищаем имя файла от недопустимых символов
            fileName = sanitizeFileName(fileName);
            // Если у файла нет расширения, добавляем формат
            if (format != null && !format.trim().isEmpty() && !fileName.toLowerCase().endsWith("." + format.toLowerCase())) {
                fileName = fileName + "." + format.toLowerCase();
            }

            // Формируем заголовок Content-Disposition для принудительного сохранения файла
            // Используем правильное кодирование UTF-8 для кириллицы (RFC 5987)
            ContentDisposition contentDisposition = ContentDisposition.attachment()
                    .filename(fileName, StandardCharsets.UTF_8)
                    .build();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);

        } catch (Exception e) {
            log.error("Ошибка при скачивании книги: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Скачивает файл книги в сжатом виде (zip) для формата fb2
     *
     * @param bookId ID книги в БД
     * @return сжатый файл для скачивания
     */
    @GetMapping("/download-zip")
    public ResponseEntity<Resource> downloadBookAsZip(@RequestParam Long bookId) {

        try {
            // Получаем информацию о книге из БД
            BookEntity bookEntity = bookRepository.findById(bookId)
                    .orElse(null);

            if (bookEntity == null) {
                log.error("Книга с ID {} не найдена в БД", bookId);
                return ResponseEntity.notFound().build();
            }

            String format = bookEntity.getFormat();
            // Проверяем, что формат fb2
            if (format == null || !format.equalsIgnoreCase("fb2")) {
                log.error("Сжатое скачивание доступно только для формата fb2, текущий формат: {}", format);
                return ResponseEntity.badRequest().build();
            }

            String archivePath = bookEntity.getArchivePath();
            String filePath = bookEntity.getFilePath() + "." + format;

            if (archivePath == null || filePath == null) {
                log.error("Не указан путь к архиву или файлу для книги с ID {}", bookId);
                return ResponseEntity.notFound().build();
            }

            // Определяем полный путь к архиву
            Path archiveFullPath;
            if (inpxProperties.getBookDirectory() != null && !inpxProperties.getBookDirectory().isEmpty()) {
                // Используем отдельную директорию для книг
                Path bookDir = Paths.get(inpxProperties.getBookDirectory());
                archiveFullPath = bookDir.resolve(archivePath);
            } else if (inpxProperties.getFilePath() != null && !inpxProperties.getFilePath().isEmpty()) {
                // Если bookDirectory не указан, используем директорию INPX файла
                Path inpxPath = Paths.get(inpxProperties.getFilePath());
                archiveFullPath = inpxPath.getParent().resolve(archivePath);
            } else {
                // Если не указаны ни bookDirectory, ни filePath, выдаем ошибку
                log.error("Не задан путь к директории с книгами (bookDirectory) или к inpx-файлу (filePath)");
                return ResponseEntity.badRequest().build();
            }

            if (!Files.exists(archiveFullPath)) {
                log.error("Архив не найден: {}", archiveFullPath);
                return ResponseEntity.notFound().build();
            }

            // Открываем архив и извлекаем файл
            byte[] fileContent;
            try (ZipFile zipFile = new ZipFile(archiveFullPath.toFile())) {
                ZipEntry entry = zipFile.getEntry(filePath);
                if (entry == null) {
                    entry = zipFile.getEntry(filePath.replace("/", "\\"));
                }
                if (entry == null) {
                    entry = zipFile.getEntry(filePath.replace("\\", "/"));
                }

                if (entry == null) {
                    log.error("Файл не найден в архиве: {} в {}", filePath, archiveFullPath);
                    return ResponseEntity.notFound().build();
                }

                // Читаем весь файл из архива в память
                try (InputStream inputStream = zipFile.getInputStream(entry)) {
                    fileContent = inputStream.readAllBytes();
                }
            }

            // Сжимаем файл в zip архив
            byte[] zipContent;
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 ZipOutputStream zos = new ZipOutputStream(baos)) {

                // Определяем имя файла для zip архива
                String fileName = bookEntity.getTitle();
                if (bookEntity.getSeries() != null && !bookEntity.getSeries().isEmpty() && bookEntity.getSeriesNumber() != null) {
                    String seriesNumber = bookEntity.getSeriesNumber().toString();
                    if (seriesNumber.length() == 1) {
                        seriesNumber = "0" + seriesNumber;
                    }
                    fileName = seriesNumber + "." + fileName;
                }
                fileName = sanitizeFileName(fileName);
                String zipEntryName = fileName + ".fb2";

                // Добавляем файл в zip архив
                ZipEntry zipEntry = new ZipEntry(zipEntryName);
                zipEntry.setSize(fileContent.length);
                zos.putNextEntry(zipEntry);
                zos.write(fileContent);
                zos.closeEntry();

                zos.finish();
                zipContent = baos.toByteArray();
            }

            // Создаем ресурс из сжатых данных
            ByteArrayResource resource = new ByteArrayResource(zipContent);

            // Определяем имя zip файла для скачивания
            String zipFileName = bookEntity.getTitle();
            if (bookEntity.getSeries() != null && !bookEntity.getSeries().isEmpty() && bookEntity.getSeriesNumber() != null) {
                String seriesNumber = bookEntity.getSeriesNumber().toString();
                if (seriesNumber.length() == 1) {
                    seriesNumber = "0" + seriesNumber;
                }
                zipFileName = seriesNumber + "." + zipFileName;
            }
            zipFileName = sanitizeFileName(zipFileName) + ".fb2.zip";

            // Формируем заголовок Content-Disposition
            ContentDisposition contentDisposition = ContentDisposition.attachment()
                    .filename(zipFileName, StandardCharsets.UTF_8)
                    .build();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);

        } catch (Exception e) {
            log.error("Ошибка при скачивании книги в zip: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Очищает имя файла от недопустимых символов
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return "book";
        }
        // Заменяем недопустимые символы для файловой системы
        return fileName.replaceAll("[<>:\"/\\|?*]", "_")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
