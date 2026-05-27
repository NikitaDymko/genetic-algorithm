import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

import javax.imageio.ImageIO;

public class GeneticAlgorithmApp {
    private static final Random RANDOM = new Random();

    private static final class Config {
        private static final int[] DOMAIN = {5, 6, 7, 8, 9};
        private static final int CHROMOSOME_LENGTH =
                (int) Math.ceil(Math.log(DOMAIN.length) / Math.log(2));

        private int populationSize = 10;
        private double crossingOverProbability = 0.7;
        private double mutationProbability = 0.2;
        private int generationNumber = 50;
        private int selectionPairs = 1;
    }

    private record Specimen(int[] chromosome, int value) {
        Specimen copy() {
            return new Specimen(chromosome.clone(), value);
        }
    }

    private record ParentPair(int first, int second) {
    }

    public static void main(String[] args) throws IOException {
        System.setProperty("java.awt.headless", "true");

        Config config = new Config();
        try (Scanner scanner = new Scanner(System.in)) {
            readConfig(scanner, config);

            List<Specimen> currentGeneration = new ArrayList<>(config.populationSize);
            System.out.print("Введите генерацию: 1 - стратегия одеяла, 2 - стратегия фокусировки\n- ");
            int generationType = readInt(scanner, 1, 2);
            if (generationType == 1) {
                blanketGeneration(currentGeneration, config);
            } else {
                focusingGeneration(currentGeneration, config);
            }

            List<Integer> results = runAlgorithm(currentGeneration, config);
            Path chartPath = Path.of("genetic_algorithm_function_values.png");
            saveChart(results, chartPath);

            Specimen best = currentGeneration.stream()
                    .min(Comparator.comparingInt(Specimen::value))
                    .orElseThrow();
            System.out.println("Минимальное значение функции: " + best.value());
            System.out.println("Лучший x: " + chromosomeToX(best.chromosome()));
            System.out.println("График сохранен: " + chartPath);
        }
    }

    private static List<Integer> runAlgorithm(List<Specimen> currentGeneration, Config config) {
        List<Integer> results = new ArrayList<>();

        for (int generation = 0; generation < config.generationNumber; generation++) {
            List<ParentPair> parents = new ArrayList<>();
            parents.addAll(randomSelection(config));
            parents.addAll(inbreedingSelection(currentGeneration, config));

            List<Specimen> descendants = new ArrayList<>();
            onePointCrossingOver(parents, currentGeneration, descendants, config);
            twoPointCrossingOver(parents, currentGeneration, descendants, config);
            universalCrossingOver(parents, currentGeneration, descendants, config);
            fibonacciCrossingOver(parents, currentGeneration, descendants, config);

            goldenRatioExchangeMutation(descendants, config);
            inversionMutation(descendants, config);

            Specimen selectedDescendant = proportionalSelection(descendants);
            results.add(selectedDescendant.value());

            int worstIndex = findWorstIndex(currentGeneration);
            if (selectedDescendant.value() < currentGeneration.get(worstIndex).value()) {
                currentGeneration.set(worstIndex, selectedDescendant);
            }
        }

        Specimen best = currentGeneration.stream()
                .min(Comparator.comparingInt(Specimen::value))
                .orElseThrow();
        results.set(results.size() - 1, best.value());
        return results;
    }

    private static void readConfig(Scanner scanner, Config config) {
        if (ask(scanner, "Будете ли вводить размер популяции?")) {
            config.populationSize = readInt(scanner, "Введите размер популяции [10;100]:", 10, 100);
        }
        if (ask(scanner, "Будете ли вводить вероятность скрещивания?")) {
            config.crossingOverProbability = readDouble(scanner, "Введите вероятность скрещивания [0.1;1.0]:", 0.1, 1.0);
        }
        if (ask(scanner, "Будете ли вводить вероятность мутации?")) {
            config.mutationProbability = readDouble(scanner, "Введите вероятность мутации [0.1;1.0]:", 0.1, 1.0);
        }
        if (ask(scanner, "Будете ли вводить количество поколений?")) {
            config.generationNumber = readInt(scanner, "Введите количество поколений [50;1000]:", 50, 1000);
        }
    }

    private static boolean ask(Scanner scanner, String question) {
        System.out.print(question + " (1 - да, 2 - нет)\n- ");
        return readInt(scanner, 1, 2) == 1;
    }

    private static int readInt(Scanner scanner, String prompt, int min, int max) {
        System.out.print(prompt + "\n- ");
        return readInt(scanner, min, max);
    }

    private static int readInt(Scanner scanner, int min, int max) {
        while (true) {
            String line = scanner.nextLine().trim();
            try {
                int value = Integer.parseInt(line);
                if (value >= min && value <= max) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // повторяем ввод ниже
            }
            System.out.printf("Введите целое число в диапазоне [%d;%d]:%n- ", min, max);
        }
    }

    private static double readDouble(Scanner scanner, String prompt, double min, double max) {
        System.out.print(prompt + "\n- ");
        while (true) {
            String line = scanner.nextLine().trim().replace(',', '.');
            try {
                double value = Double.parseDouble(line);
                if (value >= min && value <= max) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // повторяем ввод ниже
            }
            System.out.printf("Введите число в диапазоне [%.1f;%.1f]:%n- ", min, max);
        }
    }

    private static void blanketGeneration(List<Specimen> generation, Config config) {
        generation.clear();
        for (int i = 0; i < config.populationSize; i++) {
            generation.add(specimenFromIndex(i % Config.DOMAIN.length));
        }
    }

    private static void focusingGeneration(List<Specimen> generation, Config config) {
        generation.clear();
        for (int i = 0; i < config.populationSize; i++) {
            int index = 1 + RANDOM.nextInt(Config.DOMAIN.length - 2);
            generation.add(specimenFromIndex(index));
        }
    }

    private static List<ParentPair> randomSelection(Config config) {
        List<ParentPair> pairs = new ArrayList<>();
        for (int i = 0; i < config.selectionPairs; i++) {
            int first = RANDOM.nextInt(config.populationSize);
            int second = RANDOM.nextInt(config.populationSize);
            if (first == second) {
                second = second == 0 ? 1 : second - 1;
            }
            pairs.add(new ParentPair(first, second));
        }
        return pairs;
    }

    private static List<ParentPair> inbreedingSelection(List<Specimen> generation, Config config) {
        List<ParentPair> pairs = new ArrayList<>();
        for (int i = 0; i < config.selectionPairs; i++) {
            int first = RANDOM.nextInt(config.populationSize);
            int second = -1;
            int bestDistance = Integer.MAX_VALUE;

            for (int candidate = 0; candidate < generation.size(); candidate++) {
                if (candidate == first) {
                    continue;
                }
                int distance = hammingDistance(
                        generation.get(first).chromosome(),
                        generation.get(candidate).chromosome());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    second = candidate;
                }
            }
            pairs.add(new ParentPair(first, second));
        }
        return pairs;
    }

    private static void onePointCrossingOver(
            List<ParentPair> parents, List<Specimen> current, List<Specimen> next, Config config) {
        for (ParentPair pair : parents) {
            if (RANDOM.nextDouble() < config.crossingOverProbability) {
                addChildren(current.get(pair.first()), current.get(pair.second()), next,
                        1 + RANDOM.nextInt(Config.CHROMOSOME_LENGTH - 1));
            }
        }
    }

    private static void twoPointCrossingOver(
            List<ParentPair> parents, List<Specimen> current, List<Specimen> next, Config config) {
        for (ParentPair pair : parents) {
            if (RANDOM.nextDouble() >= config.crossingOverProbability) {
                continue;
            }
            int left = 1 + RANDOM.nextInt(Config.CHROMOSOME_LENGTH - 1);
            int right = 1 + RANDOM.nextInt(Config.CHROMOSOME_LENGTH - 1);
            if (left == right) {
                right = right < Config.CHROMOSOME_LENGTH - 1 ? right + 1 : right - 1;
            }
            if (left > right) {
                int temp = left;
                left = right;
                right = temp;
            }

            int[] first = current.get(pair.first()).chromosome().clone();
            int[] second = current.get(pair.second()).chromosome().clone();
            for (int i = left; i < right; i++) {
                int temp = first[i];
                first[i] = second[i];
                second[i] = temp;
            }
            addIfValid(first, next);
            addIfValid(second, next);
        }
    }

    private static void universalCrossingOver(
            List<ParentPair> parents, List<Specimen> current, List<Specimen> next, Config config) {
        for (ParentPair pair : parents) {
            if (RANDOM.nextDouble() >= config.crossingOverProbability) {
                continue;
            }
            int[] firstParent = current.get(pair.first()).chromosome();
            int[] secondParent = current.get(pair.second()).chromosome();
            int[] first = new int[Config.CHROMOSOME_LENGTH];
            int[] second = new int[Config.CHROMOSOME_LENGTH];

            for (int i = 0; i < Config.CHROMOSOME_LENGTH; i++) {
                if (RANDOM.nextBoolean()) {
                    first[i] = firstParent[i];
                    second[i] = secondParent[i];
                } else {
                    first[i] = secondParent[i];
                    second[i] = firstParent[i];
                }
            }
            addIfValid(first, next);
            addIfValid(second, next);
        }
    }

    private static void fibonacciCrossingOver(
            List<ParentPair> parents, List<Specimen> current, List<Specimen> next, Config config) {
        List<Integer> cuts = fibonacciCuts();
        for (ParentPair pair : parents) {
            if (RANDOM.nextDouble() >= config.crossingOverProbability) {
                continue;
            }
            int[] first = new int[Config.CHROMOSOME_LENGTH];
            int[] second = new int[Config.CHROMOSOME_LENGTH];

            for (int segment = 0; segment <= cuts.size(); segment++) {
                int start = segment == 0 ? 0 : cuts.get(segment - 1);
                int end = segment < cuts.size() ? cuts.get(segment) : Config.CHROMOSOME_LENGTH;
                Specimen firstSource = segment % 2 == 0 ? current.get(pair.first()) : current.get(pair.second());
                Specimen secondSource = segment % 2 == 0 ? current.get(pair.second()) : current.get(pair.first());
                for (int i = start; i < end; i++) {
                    first[i] = firstSource.chromosome()[i];
                    second[i] = secondSource.chromosome()[i];
                }
            }
            addIfValid(first, next);
            addIfValid(second, next);
        }
    }

    private static List<Integer> fibonacciCuts() {
        List<Integer> cuts = new ArrayList<>();
        int a = 1;
        int b = 1;
        while (b < Config.CHROMOSOME_LENGTH) {
            if (cuts.isEmpty() || cuts.get(cuts.size() - 1) != b) {
                cuts.add(b);
            }
            int next = a + b;
            a = b;
            b = next;
        }
        return cuts;
    }

    private static void addChildren(Specimen firstParent, Specimen secondParent, List<Specimen> next, int cut) {
        int[] first = new int[Config.CHROMOSOME_LENGTH];
        int[] second = new int[Config.CHROMOSOME_LENGTH];
        for (int i = 0; i < Config.CHROMOSOME_LENGTH; i++) {
            first[i] = i < cut ? firstParent.chromosome()[i] : secondParent.chromosome()[i];
            second[i] = i < cut ? secondParent.chromosome()[i] : firstParent.chromosome()[i];
        }
        addIfValid(first, next);
        addIfValid(second, next);
    }

    private static void goldenRatioExchangeMutation(List<Specimen> generation, Config config) {
        int firstIndex = Math.max(0, (int) Math.round((Config.CHROMOSOME_LENGTH - 1) * 0.382));
        int secondIndex = Math.min(Config.CHROMOSOME_LENGTH - 1,
                (int) Math.round((Config.CHROMOSOME_LENGTH - 1) * 0.618));

        for (int i = 0; i < generation.size(); i++) {
            if (RANDOM.nextDouble() >= config.mutationProbability) {
                continue;
            }
            int[] chromosome = generation.get(i).chromosome().clone();
            swap(chromosome, firstIndex, secondIndex);
            if (chromosomeToIndex(chromosome) >= Config.DOMAIN.length) {
                generation.remove(i--);
            } else {
                generation.set(i, specimenFromChromosome(chromosome));
            }
        }
    }

    private static void inversionMutation(List<Specimen> generation, Config config) {
        for (int i = 0; i < generation.size(); i++) {
            if (RANDOM.nextDouble() >= config.mutationProbability) {
                continue;
            }
            int[] chromosome = generation.get(i).chromosome().clone();
            int left = RANDOM.nextInt(Config.CHROMOSOME_LENGTH);
            int right = RANDOM.nextInt(Config.CHROMOSOME_LENGTH);
            if (left > right) {
                int temp = left;
                left = right;
                right = temp;
            }
            reverse(chromosome, left, right + 1);

            if (chromosomeToIndex(chromosome) >= Config.DOMAIN.length) {
                generation.remove(i--);
            } else {
                generation.set(i, specimenFromChromosome(chromosome));
            }
        }
    }

    private static Specimen proportionalSelection(List<Specimen> generation) {
        if (generation.isEmpty()) {
            return specimenFromIndex(0);
        }

        int maxValue = generation.stream().mapToInt(Specimen::value).max().orElse(0);
        double totalWeight = 0.0;
        double[] weights = new double[generation.size()];
        for (int i = 0; i < generation.size(); i++) {
            weights[i] = maxValue - generation.get(i).value() + 1.0;
            totalWeight += weights[i];
        }

        double point = RANDOM.nextDouble() * totalWeight;
        double sum = 0.0;
        for (int i = 0; i < generation.size(); i++) {
            sum += weights[i];
            if (point <= sum) {
                return generation.get(i).copy();
            }
        }
        return generation.get(generation.size() - 1).copy();
    }

    private static int findWorstIndex(List<Specimen> generation) {
        int worstIndex = 0;
        for (int i = 1; i < generation.size(); i++) {
            if (generation.get(i).value() > generation.get(worstIndex).value()) {
                worstIndex = i;
            }
        }
        return worstIndex;
    }

    private static void addIfValid(int[] chromosome, List<Specimen> generation) {
        int index = chromosomeToIndex(chromosome);
        if (index < Config.DOMAIN.length) {
            generation.add(new Specimen(chromosome, function(Config.DOMAIN[index])));
        }
    }

    private static Specimen specimenFromIndex(int index) {
        int[] chromosome = new int[Config.CHROMOSOME_LENGTH];
        int value = index;
        for (int i = Config.CHROMOSOME_LENGTH - 1; i >= 0; i--) {
            chromosome[i] = value % 2;
            value /= 2;
        }
        return new Specimen(chromosome, function(Config.DOMAIN[index]));
    }

    private static Specimen specimenFromChromosome(int[] chromosome) {
        return new Specimen(chromosome, function(chromosomeToX(chromosome)));
    }

    private static int chromosomeToIndex(int[] chromosome) {
        int index = 0;
        for (int bit : chromosome) {
            index = index * 2 + bit;
        }
        return index;
    }

    private static int chromosomeToX(int[] chromosome) {
        return Config.DOMAIN[chromosomeToIndex(chromosome)];
    }

    private static int function(int x) {
        return (int) Math.pow(x, 3) + 2 * (int) Math.pow(x, 2);
    }

    private static int hammingDistance(int[] first, int[] second) {
        int distance = 0;
        for (int i = 0; i < first.length; i++) {
            if (first[i] != second[i]) {
                distance++;
            }
        }
        return distance;
    }

    private static void swap(int[] values, int first, int second) {
        int temp = values[first];
        values[first] = values[second];
        values[second] = temp;
    }

    private static void reverse(int[] values, int left, int right) {
        for (int i = left, j = right - 1; i < j; i++, j--) {
            swap(values, i, j);
        }
    }

    private static void saveChart(List<Integer> values, Path path) throws IOException {
        int width = 1200;
        int height = 800;
        int leftMargin = 150;
        int bottomMargin = 100;
        int topMargin = 60;
        int rightMargin = 50;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        int graphWidth = width - leftMargin - rightMargin;
        int graphHeight = height - bottomMargin - topMargin;
        int maxValue = values.stream().max(Integer::compareTo).orElse(1);
        double yMax = Math.max(1.0, maxValue * 1.1);

        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        for (int i = 0; i <= 14; i++) {
            int x = leftMargin + i * graphWidth / 14;
            g.setColor(new Color(220, 220, 220));
            g.drawLine(x, topMargin, x, height - bottomMargin);
            g.setColor(Color.BLACK);
            String label = String.valueOf(1 + i * Math.max(values.size() - 1, 1) / 14);
            g.drawString(label, x - 10, height - bottomMargin + 25);
        }
        for (int i = 0; i <= 10; i++) {
            int y = height - bottomMargin - i * graphHeight / 10;
            g.setColor(new Color(220, 220, 220));
            g.drawLine(leftMargin, y, width - rightMargin, y);
            g.setColor(Color.BLACK);
            g.drawString(String.valueOf((int) (i * yMax / 10)), leftMargin - 70, y + 5);
        }

        int optimalValue = 175;
        int optimalY = height - bottomMargin - (int) (optimalValue * graphHeight / yMax);
        g.setColor(new Color(255, 150, 0)); // оранжевый
        g.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
            0, new float[]{10, 6}, 0)); // пунктир
        g.drawLine(leftMargin, optimalY, width - rightMargin, optimalY);
        g.setFont(new Font("SansSerif", Font.ITALIC, 13));
        g.drawString("f(5) = 175", width - rightMargin - 80, optimalY - 6);

        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(2));
        g.drawLine(leftMargin, height - bottomMargin, width - rightMargin, height - bottomMargin);
        g.drawLine(leftMargin, height - bottomMargin, leftMargin, topMargin);

        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        g.drawString("Minimum function value by generation", width / 2 - 185, 30);
        g.setFont(new Font("SansSerif", Font.PLAIN, 18));
        g.drawString("Generation number", width / 2 - 85, height - 40);

        g.setStroke(new BasicStroke(2));
        g.setColor(new Color(34, 139, 34));
        for (int i = 0; i < values.size() - 1; i++) {
            int x1 = leftMargin + (values.size() == 1 ? 0 : i * graphWidth / (values.size() - 1));
            int y1 = height - bottomMargin - (int) (values.get(i) * graphHeight / yMax);
            int x2 = leftMargin + (i + 1) * graphWidth / (values.size() - 1);
            int y2 = height - bottomMargin - (int) (values.get(i + 1) * graphHeight / yMax);
            g.drawLine(x1, y1, x2, y2);
        }

        for (int i = 0; i < values.size(); i++) {
            int x = leftMargin + (values.size() == 1 ? 0 : i * graphWidth / (values.size() - 1));
            int y = height - bottomMargin - (int) (values.get(i) * graphHeight / yMax);
            boolean last = i == values.size() - 1;
            g.setColor(last ? Color.RED : new Color(34, 139, 34));
            int radius = last ? 7 : 5;
            g.fillOval(x - radius, y - radius, radius * 2, radius * 2);
        }

        g.dispose();
        ImageIO.write(image, "png", path.toFile());
    }
}
