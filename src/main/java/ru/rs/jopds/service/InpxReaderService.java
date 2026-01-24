package ru.rs.jopds.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.rs.jopds.model.Book;
import ru.rs.jopds.model.BookAuthor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Сервис для чтения INPX файлов и составления каталога книг
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InpxReaderService {

    private static final String STRUCTURE_FILE = "structure.info";
    private static final String INP_EXTENSION = ".inp";
    // INP формат обычно использует табуляцию как разделитель, иногда символ с кодом 0x04
    private static final String[] DELIMITERS = {"\t", "\u0004"};

    /**
     * Читает INPX файл по полному пути
     *
     * @param filePath полный путь к INPX файлу
     * @return список книг из каталога
     */
    public List<Book> readInpxFileByPath(String filePath) {
        Path inpxPath = Paths.get(filePath);

        if (!Files.exists(inpxPath)) {
            throw new IllegalArgumentException("INPX файл не найден: " + filePath);
        }

        if (!Files.isRegularFile(inpxPath)) {
            throw new IllegalArgumentException("Указанный путь не является файлом: " + filePath);
        }

        return readInpxFileFromPath(inpxPath);
    }

    /**
     * Читает INPX файл из указанной директории и составляет каталог книг
     *
     * @param directoryPath путь к директории с INPX файлами
     * @param inpxFileName  имя INPX файла (если null, ищет первый .inpx файл)
     * @return список книг из каталога
     */
    public List<Book> readInpxFile(String directoryPath, String inpxFileName) {
        Path dirPath = Paths.get(directoryPath);

        if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
            throw new IllegalArgumentException("Директория не существует: " + directoryPath);
        }

        Path inpxPath;
        if (inpxFileName != null && !inpxFileName.isEmpty()) {
            inpxPath = dirPath.resolve(inpxFileName);
        } else {
            // Ищем первый .inpx файл в директории
            try (Stream<Path> paths = Files.list(dirPath)) {
                Optional<Path> inpxFile = paths
                        .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".inpx"))
                        .findFirst();

                if (inpxFile.isEmpty()) {
                    throw new IllegalArgumentException("INPX файл не найден в директории: " + directoryPath);
                }
                inpxPath = inpxFile.get();
            } catch (IOException e) {
                throw new RuntimeException("Ошибка при поиске INPX файла: " + e.getMessage(), e);
            }
        }

        if (!Files.exists(inpxPath)) {
            throw new IllegalArgumentException("INPX файл не найден: " + inpxPath);
        }

        return readInpxFileFromPath(inpxPath);
    }

    /**
     * Читает INPX файл из указанного пути (внутренний метод)
     */
    private List<Book> readInpxFileFromPath(Path inpxPath) {
        log.info("Чтение INPX файла: {}", inpxPath);

        List<Book> books = new ArrayList<>();

        try (ZipFile zipFile = new ZipFile(inpxPath.toFile(), StandardCharsets.UTF_8)) {
            // Читаем структуру файла
            List<String> structure = readStructure(zipFile);
            log.debug("Структура INP файла: {}", structure);

            // Читаем все INP файлы из архива
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();

                if (entryName.toLowerCase().endsWith(INP_EXTENSION)) {
                    log.info("Обработка INP файла: {}", entryName);
                    List<Book> booksFromInp = parseInpFile(zipFile, entry, structure, entryName);
                    books.addAll(booksFromInp);
                }
            }

            log.info("Всего найдено книг: {}", books.size());

        } catch (IOException e) {
            throw new RuntimeException("Ошибка при чтении INPX файла: " + e.getMessage(), e);
        }

        return books;
    }

    /**
     * Читает структуру INP файла (названия колонок)
     */
    private List<String> readStructure(ZipFile zipFile) throws IOException {
        ZipEntry structureEntry = zipFile.getEntry(STRUCTURE_FILE);

        if (structureEntry == null) {
            // Если файла structure.info нет, используем стандартную структуру
            log.warn("Файл structure.info не найден, используется стандартная структура");
            return getDefaultStructure();
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(zipFile.getInputStream(structureEntry), StandardCharsets.UTF_8))) {

            return reader.lines()
                    .filter(line -> !line.trim().isEmpty())
                    .collect(Collectors.toList());
        }
    }

    /**
     * Возвращает стандартную структуру INP файла
     */
    private List<String> getDefaultStructure() {
        return Arrays.asList(
                "Authors", "Genres", "Title", "Series", "SeriesNumber", "FilePath", "FileSize", "LibId", "Del", "Format", "Date",
                "Language", "AdditionalInfo"
        );
    }

    /**
     * Парсит INP файл и возвращает список книг
     */
    private List<Book> parseInpFile(ZipFile zipFile, ZipEntry entry, List<String> structure, String entryName) throws IOException {
        List<Book> books = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(zipFile.getInputStream(entry), StandardCharsets.UTF_8))) {

            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                try {
                    Book book = parseBookLine(line, structure, entryName);
                    if (book != null) {
                        books.add(book);
                    }
                } catch (Exception e) {
                    log.warn("Ошибка при парсинге строки {} в файле {}: {}",
                            lineNumber, entry.getName(), e.getMessage());
                }
            }
        }

        return books;
    }

    /**
     * Парсит одну строку INP файла в объект Book
     */
    private Book parseBookLine(String line, List<String> structure, String entryName) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }

        // INP формат обычно использует табуляцию, иногда символ с кодом 0x04
        // Пробуем различные разделители
        String[] fields = null;
        for (String delimiter : DELIMITERS) {
            if (line.contains(delimiter)) {
                fields = line.split(delimiter, -1);
                break;
            }
        }
        // Если ни один разделитель не найден, используем табуляцию по умолчанию
        if (fields == null) {
            fields = line.split(DELIMITERS[0], -1);
        }

        // Нормализуем количество полей (добавляем пустые строки если не хватает)
        String[] normalizedFields = new String[structure.size()];
        for (int i = 0; i < normalizedFields.length; i++) {
            normalizedFields[i] = (i < fields.length) ? fields[i] : "";
        }

        // Создаем мапу для удобного доступа к полям
        Map<String, String> fieldMap = new HashMap<>();
        for (int i = 0; i < structure.size() && i < normalizedFields.length; i++) {
            fieldMap.put(structure.get(i), normalizedFields[i]);
        }

        // Парсим основные поля
        String authorsStr = getField(fieldMap, "Authors", 0);
        String genresStr = getField(fieldMap, "Genres", 1);
        String title = getField(fieldMap, "Title", 2);
        String series = getField(fieldMap, "Series", 3);
        String seriesNumberStr = getField(fieldMap, "SeriesNumber", 4);
        String filePath = getField(fieldMap, "FilePath", 5);
        String fileSizeStr = getField(fieldMap, "FileSize", 6);
        String format = getField(fieldMap, "Format", 9);
        String dateStr = getField(fieldMap, "Date", 10);
        String language = getField(fieldMap, "Language", 11);
        String additionalInfo = getField(fieldMap, "AdditionalInfo", 12);

        // Парсим авторов (разделитель: точка с запятой или двоеточие)
        List<BookAuthor> authors = parseAuthors(authorsStr, "[;:]");

        // Парсим жанры
        List<String> genres = parseList(genresStr, "[;:]");

        // Парсим номер в серии
        Integer seriesNumber = null;
        try {
            if (seriesNumberStr != null && !seriesNumberStr.trim().isEmpty()) {
                // Убираем дробную часть если есть (например, "1.0" -> "1")
                String cleanNumber = seriesNumberStr.trim().replaceFirst("\\.0*$", "");
                seriesNumber = Integer.parseInt(cleanNumber);
            }
        } catch (NumberFormatException e) {
            log.debug("Не удалось распарсить номер серии: {}", seriesNumberStr);
        }

        // Парсим размер файла
        Long fileSize = null;
        try {
            if (fileSizeStr != null && !fileSizeStr.trim().isEmpty()) {
                fileSize = Long.parseLong(fileSizeStr.trim());
            }
        } catch (NumberFormatException e) {
            log.debug("Не удалось распарсить размер файла: {}", fileSizeStr);
        }

        // Парсим дату
        LocalDate date = null;
        if (dateStr != null && !dateStr.trim().isEmpty()) {
            date = parseDate(dateStr.trim());
        }
        String authorsString = authorsStr.replace(',', ' ').replace(":", " ,").substring(0, authorsStr.replace(',', ' ').replace(":", " ,").length() - 2);
        return Book.builder()
                .title(title)
                .authorsString(authorsString)
                .authors(authors)
                .series(series)
                .seriesNumber(seriesNumber)
                .genres(genres)
                .language(language)
                .format(format)
                .fileSize(fileSize)
                .date(date)
                .filePath(filePath)
                .additionalInfo(additionalInfo)
                .archivePath(entryName.replace(".inp", ".zip"))
                .build();
    }

    /**
     * Получает значение поля по имени или по индексу структуры
     */
    private String getField(Map<String, String> fieldMap, String fieldName, int defaultIndex) {
        String value = fieldMap.get(fieldName);
        if (value == null) {
            // Пробуем получить по индексу в порядке структуры
            List<String> keys = new ArrayList<>(fieldMap.keySet());
            if (defaultIndex < keys.size()) {
                return fieldMap.get(keys.get(defaultIndex));
            }
        }
        return value != null ? value.trim() : "";
    }

    /**
     * Парсит строку со списком авторов
     */
    public List<BookAuthor> parseAuthors(String str, String delimiterRegex) {
        List<BookAuthor> result = new ArrayList<>();
        if (str == null || str.trim().isEmpty()) {
            return result;
        }

        //Авторы разделены двоеточием. Фамилия, имя, отчество разделены запятой
        String[] authors = str.split(delimiterRegex);
        for (String author : authors) {
            String[] authorParts = author.split(",");
            String lastName = "";
            String firstName = "";
            String middleName = "";
            if (authorParts.length > 0) {
                lastName = authorParts[0].trim().toLowerCase();
                if (lastName.isEmpty()) {
                    lastName = "_";
                }
            }
            if (authorParts.length > 1) {
                firstName = authorParts[1].trim().toLowerCase();
            }
            if (authorParts.length > 2) {
                middleName = authorParts[2].trim().toLowerCase();
            }
            result.add(BookAuthor.builder().lastName(lastName).firstName(firstName).middleName(middleName).fullName(author.replace(',', ' ').trim()).build());
        }
        return result;
    }

    /**
     * Парсит строку со списком значений в список строк
     */
    private List<String> parseList(String str, String delimiterRegex) {
        if (str == null || str.trim().isEmpty()) {
            return new ArrayList<>();
        }

        return Arrays.stream(str.split(delimiterRegex))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Парсит дату в различных форматах
     */
    private LocalDate parseDate(String dateStr) {
        // Пробуем различные форматы даты
        DateTimeFormatter[] formatters = {
                DateTimeFormatter.ISO_LOCAL_DATE,           // yyyy-MM-dd
                DateTimeFormatter.ofPattern("dd.MM.yyyy"),  // dd.MM.yyyy
                DateTimeFormatter.ofPattern("yyyy.MM.dd"),  // yyyy.MM.dd
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),  // dd/MM/yyyy
                DateTimeFormatter.ofPattern("yyyy/MM/dd")   // yyyy/MM/dd
        };

        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(dateStr, formatter);
            } catch (DateTimeParseException e) {
                // Пробуем следующий формат
            }
        }

        log.debug("Не удалось распарсить дату: {}", dateStr);
        return null;
    }
}

