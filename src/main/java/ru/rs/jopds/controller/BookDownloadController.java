package ru.rs.jopds.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
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

import java.io.BufferedOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

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

            // Имя файла и заголовки формируем до открытия архива
            String fileName = buildBookFileName(bookEntity);
            // Если у файла нет расширения, добавляем формат
            if (format != null && !format.trim().isEmpty() && !fileName.toLowerCase().endsWith("." + format.toLowerCase())) {
                fileName = fileName + "." + format.toLowerCase();
            }
            ContentDisposition contentDisposition = ContentDisposition.attachment()
                    .filename(fileName, StandardCharsets.UTF_8)
                    .build();

            // Открываем архив и отдаём файл потоком: книга не читается в память целиком,
            // ZipFile закрывается вместе с потоком ответа (после отправки тела)
            ZipFile zipFile = new ZipFile(archiveFullPath.toFile());
            try {
                ZipEntry entry = findZipEntry(zipFile, filePath);
                if (entry == null) {
                    zipFile.close();
                    log.error("Файл не найден в архиве: {} в {}", filePath, archiveFullPath);
                    return ResponseEntity.notFound().build();
                }

                Resource resource = new InputStreamResource(
                        new ZipFileEntryStream(zipFile, zipFile.getInputStream(entry)));
                ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                        .contentType(MediaType.APPLICATION_OCTET_STREAM);
                if (entry.getSize() >= 0) {
                    response.contentLength(entry.getSize());
                }
                return response.body(resource);
            } catch (IOException e) {
                zipFile.close();
                throw e;
            }

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

            // Имя файла для скачивания
            String zipFileName = buildBookFileName(bookEntity) + ".fb2.zip";
            ContentDisposition contentDisposition = ContentDisposition.attachment()
                    .filename(zipFileName, StandardCharsets.UTF_8)
                    .build();

            // Раньше fb2 целиком читался в память и там же собирался zip (2 копии книги в heap).
            // Теперь zip пишется во временный файл и отдаётся из него потоком:
            // в памяти держится только буфер упаковки.
            Path tempZip = Files.createTempFile("jopds-download-", ".zip");
            boolean handedOff = false;
            try {
                try (ZipFile zipFile = new ZipFile(archiveFullPath.toFile());
                     ZipOutputStream zos = new ZipOutputStream(
                             new BufferedOutputStream(Files.newOutputStream(tempZip)))) {

                    ZipEntry entry = findZipEntry(zipFile, filePath);
                    if (entry == null) {
                        log.error("Файл не найден в архиве: {} в {}", filePath, archiveFullPath);
                        return ResponseEntity.notFound().build();
                    }

                    ZipEntry zipEntry = new ZipEntry(buildBookFileName(bookEntity) + ".fb2");
                    if (entry.getSize() >= 0) {
                        zipEntry.setSize(entry.getSize());
                    }
                    zos.putNextEntry(zipEntry);
                    try (InputStream inputStream = zipFile.getInputStream(entry)) {
                        inputStream.transferTo(zos);
                    }
                    zos.closeEntry();
                }

                // Отдаём готовый zip из временного файла; DELETE_ON_CLOSE удалит его,
                // когда Spring закроет поток после отправки ответа
                long zipSize = Files.size(tempZip);
                InputStream responseStream = Files.newInputStream(tempZip, StandardOpenOption.DELETE_ON_CLOSE);
                handedOff = true;

                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .contentLength(zipSize)
                        .body(new InputStreamResource(responseStream));
            } finally {
                // Если ответ не был создан (ошибка/404) — временный файл удаляем сами
                if (!handedOff) {
                    tempZip.toFile().delete();
                }
            }

        } catch (Exception e) {
            log.error("Ошибка при скачивании книги в zip: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Получает аннотацию из FB2 файла
     *
     * @param bookId ID книги в БД
     * @return JSON с аннотацией или сообщением об ошибке
     */
    @GetMapping("/annotation")
    public ResponseEntity<Map<String, String>> getAnnotation(@RequestParam Long bookId) {
        Map<String, String> response = new HashMap<>();
        
        try {
            // Получаем информацию о книге из БД
            BookEntity bookEntity = bookRepository.findById(bookId)
                    .orElse(null);

            if (bookEntity == null) {
                log.error("Книга с ID {} не найдена в БД", bookId);
                response.put("error", "Книга не найдена");
                return ResponseEntity.notFound().build();
            }

            String format = bookEntity.getFormat();
            // Проверяем, что формат fb2
            if (format == null || !format.equalsIgnoreCase("fb2")) {
                log.debug("Аннотация доступна только для формата fb2, текущий формат: {}", format);
                response.put("error", "Аннотация доступна только для формата FB2");
                return ResponseEntity.badRequest().body(response);
            }

            String archivePath = bookEntity.getArchivePath();
            String filePath = bookEntity.getFilePath() + "." + format;

            if (archivePath == null || filePath == null) {
                log.error("Не указан путь к архиву или файлу для книги с ID {}", bookId);
                response.put("error", "Путь к файлу не указан");
                return ResponseEntity.notFound().build();
            }

            // Определяем полный путь к архиву
            Path archiveFullPath;
            if (inpxProperties.getBookDirectory() != null && !inpxProperties.getBookDirectory().isEmpty()) {
                Path bookDir = Paths.get(inpxProperties.getBookDirectory());
                archiveFullPath = bookDir.resolve(archivePath);
            } else if (inpxProperties.getFilePath() != null && !inpxProperties.getFilePath().isEmpty()) {
                Path inpxPath = Paths.get(inpxProperties.getFilePath());
                archiveFullPath = inpxPath.getParent().resolve(archivePath);
            } else {
                log.error("Не задан путь к директории с книгами (bookDirectory) или к inpx-файлу (filePath)");
                response.put("error", "Путь к директории с книгами не задан");
                return ResponseEntity.badRequest().body(response);
            }

            if (!Files.exists(archiveFullPath)) {
                log.error("Архив не найден: {}", archiveFullPath);
                response.put("error", "Архив не найден");
                return ResponseEntity.notFound().build();
            }

            // Открываем архив и извлекаем начало файла
            String annotation = null;
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
                    response.put("error", "Файл не найден в архиве");
                    return ResponseEntity.notFound().build();
                }

                // Читаем начало файла (первые 200 КБ должно хватить для title-info)
                try (InputStream inputStream = zipFile.getInputStream(entry)) {
                    byte[] buffer = new byte[200 * 1024]; // 200 КБ
                    int bytesRead = inputStream.read(buffer);
                    
                    if (bytesRead > 0) {
                        // Определяем кодировку из XML декларации
                        String xmlStart = new String(buffer, 0, Math.min(bytesRead, 1000), StandardCharsets.UTF_8);
                        java.nio.charset.Charset charset = StandardCharsets.UTF_8;
                        
                        // Пытаемся найти encoding в XML декларации
                        int encodingStart = xmlStart.indexOf("encoding=\"");
                        if (encodingStart > 0) {
                            encodingStart += "encoding=\"".length();
                            int encodingEnd = xmlStart.indexOf("\"", encodingStart);
                            if (encodingEnd > 0) {
                                String encoding = xmlStart.substring(encodingStart, encodingEnd).toUpperCase();
                                try {
                                    charset = java.nio.charset.Charset.forName(encoding);
                                } catch (Exception e) {
                                    log.debug("Не удалось определить кодировку {}, используем UTF-8", encoding);
                                }
                            }
                        }
                        
                        // Читаем XML с правильной кодировкой
                        String xmlContent = new String(buffer, 0, bytesRead, charset);
                        
                        // Находим закрывающий тег description
                        int descriptionEnd = xmlContent.indexOf("</description>");

                        if (descriptionEnd > 0) {
                            // Обрезаем до закрывающего тега description
                            xmlContent = xmlContent.substring(0, descriptionEnd + "</description>".length());
                            
                            // Добавляем закрывающий тег FictionBook, если его нет
                            // Проверяем, есть ли открывающий тег FictionBook (может быть с атрибутами)
                            if ((xmlContent.contains("<FictionBook") || xmlContent.contains("<FictionBook>")) 
                                    && !xmlContent.contains("</FictionBook>")) {
                                xmlContent = xmlContent + "</FictionBook>";
                            }
                        } else {
                            // Если не нашли закрывающий тег description, пробуем найти title-info
                            int titleInfoEnd = xmlContent.indexOf("</title-info>");
                            if (titleInfoEnd > 0) {
                                xmlContent = xmlContent.substring(0, titleInfoEnd + "</title-info>".length());
                                // Добавляем недостающие закрывающие теги
                                if ((xmlContent.contains("<description") || xmlContent.contains("<description>")) 
                                        && !xmlContent.contains("</description>")) {
                                    xmlContent = xmlContent + "</description>";
                                }
                                if ((xmlContent.contains("<FictionBook") || xmlContent.contains("<FictionBook>")) 
                                        && !xmlContent.contains("</FictionBook>")) {
                                    xmlContent = xmlContent + "</FictionBook>";
                                }
                            } else {
                                // Если не нашли закрывающие теги, возможно они дальше в файле
                                int titleInfoStart = xmlContent.indexOf("<title-info");
                                if (titleInfoStart > 0) {
                                    log.debug("title-info найден, но закрывающий тег не найден в первых 200 КБ");
                                }
                            }
                        }
                        
                        // Парсим XML и извлекаем аннотацию
                        annotation = extractAnnotation(xmlContent);
                    }
                }
            }

            if (annotation != null && !annotation.trim().isEmpty()) {
                response.put("annotation", annotation);
                return ResponseEntity.ok(response);
            } else {
                response.put("message", "Аннотация не найдена");
                return ResponseEntity.ok(response);
            }

        } catch (Exception e) {
            log.error("Ошибка при получении аннотации: {}", e.getMessage(), e);
            response.put("error", "Ошибка при чтении аннотации: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Извлекает аннотацию из XML содержимого FB2 файла
     */
    private String extractAnnotation(String xmlContent) {
        try {
            if (xmlContent == null || xmlContent.trim().isEmpty()) {
                return null;
            }
            
            // Удаляем BOM (Byte Order Mark) если он есть в начале строки
            if (xmlContent.charAt(0) == '\uFEFF') {
                xmlContent = xmlContent.substring(1);
            }
            
            // Удаляем ведущие пробелы и проверяем наличие XML декларации
            String trimmed = xmlContent.trim();
            if (!trimmed.startsWith("<?xml")) {
                // Если нет XML декларации, добавляем её
                xmlContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + xmlContent.trim();
            }
            
            // Создаем DocumentBuilder
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setIgnoringElementContentWhitespace(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            
            // Парсим XML
            InputSource inputSource = new InputSource(new StringReader(xmlContent));
            Document doc = builder.parse(inputSource);
            
            // Пробуем найти title-info с namespace
            String fb2Namespace = "http://www.gribuser.ru/xml/fictionbook/2.0";
            NodeList titleInfoList = doc.getElementsByTagNameNS(fb2Namespace, "title-info");
            
            // Если не нашли с namespace, пробуем без namespace
            if (titleInfoList.getLength() == 0) {
                titleInfoList = doc.getElementsByTagName("title-info");
            }
            
            if (titleInfoList.getLength() > 0) {
                Element titleInfo = (Element) titleInfoList.item(0);
                
                // Ищем annotation с namespace
                NodeList annotationList = titleInfo.getElementsByTagNameNS(fb2Namespace, "annotation");
                
                // Если не нашли с namespace, пробуем без namespace
                if (annotationList.getLength() == 0) {
                    annotationList = titleInfo.getElementsByTagName("annotation");
                }
                
                if (annotationList.getLength() > 0) {
                    Element annotationElement = (Element) annotationList.item(0);
                    return getElementTextContent(annotationElement);
                }
            }
            
            return null;
        } catch (Exception e) {
            log.debug("Ошибка при парсинге XML для извлечения аннотации: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Получает текстовое содержимое элемента, включая все вложенные элементы
     */
    private String getElementTextContent(Element element) {
        if (element == null) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        NodeList children = element.getChildNodes();
        
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.TEXT_NODE) {
                sb.append(node.getTextContent());
            } else if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) node;
                String tagName = childElement.getTagName();
                String textContent = getElementTextContent(childElement);
                
                // Добавляем форматирование для некоторых тегов
                if ("p".equals(tagName)) {
                    sb.append(textContent).append("\n\n");
                } else if ("empty-line".equals(tagName)) {
                    sb.append("\n");
                } else {
                    sb.append(textContent);
                }
            }
        }
        
        return sb.toString().trim();
    }

    /**
     * Имя файла для скачивания: номер серии + название (без расширения)
     */
    private String buildBookFileName(BookEntity bookEntity) {
        String fileName = bookEntity.getTitle();
        //Если книга из серии, то добавляем номер серии.
        if (bookEntity.getSeries() != null && !bookEntity.getSeries().isEmpty() && bookEntity.getSeriesNumber() != null) {
            String seriesNumber = bookEntity.getSeriesNumber().toString();
            if (seriesNumber.length() == 1) {
                seriesNumber = "0" + seriesNumber;
            }
            fileName = seriesNumber + "." + fileName;
        }
        return sanitizeFileName(fileName);
    }

    /**
     * Ищет файл в архиве, перебирая варианты разделителей пути
     */
    private ZipEntry findZipEntry(ZipFile zipFile, String filePath) {
        ZipEntry entry = zipFile.getEntry(filePath);
        if (entry == null) {
            // Пробуем с обратными слешами
            entry = zipFile.getEntry(filePath.replace("/", "\\"));
        }
        if (entry == null) {
            // Пробуем с прямыми слешами
            entry = zipFile.getEntry(filePath.replace("\\", "/"));
        }
        return entry;
    }

    /**
     * Поток записи из архива: при закрытии закрывает и сам ZipFile.
     * Spring закрывает поток тела ответа после отправки — тогда же
     * освобождается и файловый дескриптор архива.
     */
    private static class ZipFileEntryStream extends FilterInputStream {
        private final ZipFile zipFile;

        ZipFileEntryStream(ZipFile zipFile, InputStream in) {
            super(in);
            this.zipFile = zipFile;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                zipFile.close();
            }
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
