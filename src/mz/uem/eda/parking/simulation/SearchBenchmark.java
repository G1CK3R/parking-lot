package mz.uem.eda.parking.simulation;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.function.Predicate;

import mz.uem.eda.parking.algorithms.SequentialSearch;
import mz.uem.eda.parking.structures.ArrayStack;
import mz.uem.eda.parking.structures.LinkedQueue;
import mz.uem.eda.parking.structures.Queue;
import mz.uem.eda.parking.structures.Stack;

/**
 * Mede a busca sequencial ({@link SequentialSearch}) numa pilha e numa fila
 * de tamanhos crescentes, para o relatório confirmar que o custo é O(n).
 *
 * <p>Para cada tamanho n procuram-se três matrículas: a do topo da pilha (ou
 * da frente da fila), o melhor caso, com 1 comparação; a do meio, com
 * n/2 + 1; e uma que não existe, o pior caso, com n. O tempo de cada caso é
 * o do lote mais rápido de {@value #BATCHES} lotes de {@value #REPEATS}
 * buscas, depois de um aquecimento: uma medição isolada em Java é ruidosa
 * (ver {@link Stopwatch}), e o ruído (o recolhedor de lixo, outros
 * programas) só torna um lote mais lento, nunca mais rápido.
 *
 * <p>O resultado vai para {@code docs/measurements/search.csv} e para a
 * consola. O que se espera ver: as comparações crescem exactamente como n,
 * e o tempo do pior caso cresce mais ou menos na mesma proporção. Com os 200
 * lugares do parque, o pior caso fica abaixo de um microssegundo.
 *
 * <p>Fica no pacote {@code simulation} porque é uma ferramenta de medição,
 * como o {@link Simulator}, e não faz parte do programa do balcão.
 */
public final class SearchBenchmark {

    /** Os tamanhos medidos: de uma baia pequena até muito mais do que o parque. */
    private static final int[] SIZES = { 25, 50, 100, 200, 400, 800, 1600, 3200 };

    /** Buscas por lote. */
    private static final int REPEATS = 2000;

    /** Lotes por caso; fica o mais rápido. */
    private static final int BATCHES = 21;

    /** Rondas de aquecimento antes de medir, para o JIT compilar a busca. */
    private static final int WARMUP_ROUNDS = 3;

    /** Os três casos, pela ordem das colunas. */
    private static final String[] CASES = { "topo", "meio", "ausente" };

    /** Soma dos resultados, só para o compilador não apagar as buscas. */
    private static long sink;

    private SearchBenchmark() {
    }

    /**
     * Corre as medições e grava o CSV.
     *
     * @param args não usados
     * @throws IOException se não for possível gravar o CSV
     */
    public static void main(String[] args) throws IOException {
        Path out = Paths.get("docs", "measurements", "search.csv");
        Files.createDirectories(out.getParent());

        for (int round = 0; round < WARMUP_ROUNDS; round++) {
            for (int n : SIZES) {
                measureAll(n);
            }
        }

        try (PrintWriter csv = new PrintWriter(Files.newBufferedWriter(out, StandardCharsets.UTF_8))) {
            csv.println("estrutura;n;caso;comparacoes;nanossegundos_por_busca");
            System.out.println("estrutura        n  caso      comparações   ns por busca");
            for (int n : SIZES) {
                double[][] times = measureAll(n);
                for (int s = 0; s < 2; s++) {
                    String structure = s == 0 ? "pilha" : "fila";
                    for (int c = 0; c < CASES.length; c++) {
                        int comparisons = comparisons(n, c);
                        csv.println(String.format(Locale.ROOT, "%s;%d;%s;%d;%.1f", structure, n, CASES[c],
                                comparisons, times[s][c]));
                        System.out.println(String.format(Locale.ROOT, "%-9s %6d  %-8s %12d %14.1f", structure, n,
                                CASES[c], comparisons, times[s][c]));
                    }
                }
            }
        }
        System.out.println();
        System.out.println("CSV gravado em " + out.toAbsolutePath() + " (controlo: " + sink + ")");
    }

    /**
     * Mede os três casos numa pilha e numa fila com n matrículas.
     *
     * @return os tempos por busca, em nanossegundos: [estrutura][caso]
     */
    private static double[][] measureAll(int n) {
        Stack<String> stack = new ArrayStack<>(n);
        Queue<String> queue = new LinkedQueue<>();
        // A matrícula i entra em i-ésimo lugar: na pilha a última fica no topo,
        // na fila a primeira fica à frente.
        for (int i = 0; i < n; i++) {
            stack.push(plate(i));
            queue.enqueue(plate(i));
        }
        String[] stackTargets = { plate(n - 1), plate(n - 1 - n / 2), "ZZZ999MC" };
        String[] queueTargets = { plate(0), plate(n / 2), "ZZZ999MC" };

        double[][] times = new double[2][CASES.length];
        for (int c = 0; c < CASES.length; c++) {
            times[0][c] = fastest(stack, stackTargets[c]);
            times[1][c] = fastest(queue, queueTargets[c]);
        }
        return times;
    }

    /** O tempo por busca do lote mais rápido, em {@value #BATCHES} lotes. */
    private static double fastest(Iterable<String> source, String target) {
        Predicate<String> isTarget = new Predicate<String>() {
            @Override
            public boolean test(String plate) {
                return plate.equals(target);
            }
        };
        long best = Long.MAX_VALUE;
        Stopwatch watch = new Stopwatch();
        for (int b = 0; b < BATCHES; b++) {
            watch.start();
            for (int r = 0; r < REPEATS; r++) {
                sink += SequentialSearch.indexOf(source, isTarget);
            }
            best = Math.min(best, watch.stop());
        }
        return best / (double) REPEATS;
    }

    /** Quantas comparações faz cada caso com n elementos. */
    private static int comparisons(int n, int caseIndex) {
        switch (caseIndex) {
            case 0:
                return 1;
            case 1:
                return n / 2 + 1;
            default:
                return n;
        }
    }

    private static String plate(int i) {
        return String.format("AAA%05d", i);
    }
}
