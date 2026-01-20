import java.util.*;

/**
 * Classe complète pour l'analyse SARIMA
 * Détecte automatiquement les paramètres p, d, q, P, D, Q, s
 */
public class ArimaParameters {

    public static class SARIMAParameters {

        public int p;
        public int d;
        public int q;


        public int P;
        public int D;
        public int Q;
        public int s;

        public double[] stationaryData;

        public double confidence;

        @Override
        public String toString() {
            return String.format("SARIMA(%d,%d,%d)(%d,%d,%d)[%d] - Confiance: %.2f%%",
                    p, d, q, P, D, Q, s, confidence * 100);
        }

        public String getDetailedReport() {
            StringBuilder sb = new StringBuilder();
            sb.append("╔════════════════════════════════════════╗\n");
            sb.append("║     ANALYSE SARIMA - RÉSULTATS         ║\n");
            sb.append("╠════════════════════════════════════════╣\n");
            sb.append(String.format("║ Paramètres Non-Saisonniers            ║\n"));
            sb.append(String.format("║   p (AR)              : %-14d║\n", p));
            sb.append(String.format("║   d (Différenciation) : %-14d║\n", d));
            sb.append(String.format("║   q (MA)              : %-14d║\n", q));
            sb.append("╠════════════════════════════════════════╣\n");
            sb.append(String.format("║ Paramètres Saisonniers                ║\n"));
            sb.append(String.format("║   P (AR)              : %-14d║\n", P));
            sb.append(String.format("║   D (Différenciation) : %-14d║\n", D));
            sb.append(String.format("║   Q (MA)              : %-14d║\n", Q));
            sb.append(String.format("║   s (Période)         : %-14d║\n", s));
            sb.append("╠════════════════════════════════════════╣\n");
            sb.append(String.format("║ Confiance : %.2f%%                    ║\n", confidence * 100));
            sb.append("╚════════════════════════════════════════╝\n");
            return sb.toString();
        }
    }

    /**
     * Analyse complète et détection automatique des paramètres SARIMA
     */
    public static SARIMAParameters analyzeSARIMA(double[] data) {
        return analyzeSARIMA(data, true);
    }

    /**
     * Analyse SARIMA avec option de verbose
     */
    public static SARIMAParameters analyzeSARIMA(double[] data, boolean verbose) {
        SARIMAParameters params = new SARIMAParameters();

        if (verbose) {
            System.out.println("╔════════════════════════════════════════╗");
            System.out.println("║   DÉMARRAGE ANALYSE SARIMA             ║");
            System.out.println("╠════════════════════════════════════════╣");
            System.out.println("║ Taille des données : " + String.format("%-18d", data.length) + "║");
            System.out.println("╚════════════════════════════════════════╝\n");
        }

        // Étape 1 : Détecter la saisonnalité
        if (verbose) System.out.println("Étape 1/5 : Détection de la saisonnalité...");
        params.s = detectSeasonality(data, verbose);

        // Étape 2 : Différenciation saisonnière si nécessaire
        double[] workingData = data.clone();
        if (params.s > 1) {
            if (verbose) System.out.println("\nÉtape 2/5 : Différenciation saisonnière...");
            SeasonalDifferenceResult seasonalResult = seasonalDifferenceIfNeeded(workingData, params.s, verbose);
            params.D = seasonalResult.order;
            workingData = seasonalResult.data;
        } else {
            if (verbose) System.out.println("\nÉtape 2/5 : Pas de saisonnalité détectée - Différenciation saisonnière ignorée");
            params.D = 0;
        }

        // Étape 3 : Différenciation non-saisonnière
        if (verbose) System.out.println("\nÉtape 3/5 : Différenciation non-saisonnière...");
        DifferencingResult diffResult = differenceIfNeeded(workingData, verbose);
        params.d = diffResult.order;
        workingData = diffResult.data;
        params.stationaryData = workingData;

        // Étape 4 : Calculer ACF et PACF
        if (verbose) System.out.println("\nÉtape 4/5 : Calcul ACF et PACF...");
        int maxLag = Math.min(50, workingData.length / 4);
        double[] acf = calculateACF(workingData, maxLag);
        double[] pacf = calculatePACF(acf, maxLag);

        // Étape 5 : Identifier p, q, P, Q
        if (verbose) System.out.println("\nÉtape 5/5 : Identification des ordres AR et MA...");

        // Identifier p et q (non-saisonniers)
        IdentificationResult nonseasonal = identifyARMA(acf, pacf, workingData.length, params.s, verbose);
        params.p = nonseasonal.p;
        params.q = nonseasonal.q;

        // Identifier P et Q (saisonniers) si s > 1
        if (params.s > 1) {
            IdentificationResult seasonal = identifySeasonalARMA(acf, pacf, workingData.length, params.s, verbose);
            params.P = seasonal.p;
            params.Q = seasonal.q;
        } else {
            params.P = 0;
            params.Q = 0;
        }

        // Calculer la confiance
        params.confidence = calculateConfidence(acf, pacf, workingData.length, params);

        if (verbose) {
            System.out.println("\n" + params.getDetailedReport());
        }

        return params;
    }

    /**
     * Détecte la période saisonnière
     */
    private static int detectSeasonality(double[] data, boolean verbose) {
        int maxLag = Math.min(100, data.length / 2);
        double[] acf = calculateACF(data, maxLag);

        List<SeasonalPeak> peaks = new ArrayList<>();

        // Chercher les pics dans l'ACF
        for (int lag = 4; lag <= maxLag - 2; lag++) {
            // Un pic est un maximum local
            if (acf[lag] > acf[lag - 1] && acf[lag] > acf[lag + 1]) {
                if (Math.abs(acf[lag]) > 0.2) {
                    double strength = Math.abs(acf[lag]);

                    // Vérifier la régularité (multiples du lag)
                    double regularity = checkRegularity(acf, lag, maxLag);

                    peaks.add(new SeasonalPeak(lag, strength, regularity));
                }
            }
        }

        // Trier par score (strength * regularity)
        peaks.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

        if (verbose) {
            System.out.println("   Pics saisonniers détectés :");
            for (int i = 0; i < Math.min(3, peaks.size()); i++) {
                SeasonalPeak peak = peaks.get(i);
                System.out.printf("      Période %d : Force=%.3f, Régularité=%.3f, Score=%.3f%n",
                        peak.lag, peak.strength, peak.regularity, peak.getScore());
            }
        }

        // Retourner le meilleur pic, ou 0 si pas de saisonnalité claire
        if (!peaks.isEmpty() && peaks.get(0).getScore() > 0.15) {
            int period = peaks.get(0).lag;
            if (verbose) System.out.println("Saisonnalité détectée : période = " + period);
            return period;
        }

        if (verbose) System.out.println("Pas de saisonnalité significative détectée");
        return 0;
    }

    /**
     * Vérifie la régularité d'une période (si les multiples sont aussi des pics)
     */
    private static double checkRegularity(double[] acf, int period, int maxLag) {
        double score = 0;
        int count = 0;

        // Vérifier les multiples 2x, 3x
        for (int mult = 2; mult <= 3; mult++) {
            int targetLag = period * mult;
            if (targetLag < maxLag) {
                // Chercher un pic autour de targetLag (±2)
                double maxNearby = 0;
                for (int i = Math.max(1, targetLag - 2); i <= Math.min(maxLag - 1, targetLag + 2); i++) {
                    maxNearby = Math.max(maxNearby, Math.abs(acf[i]));
                }
                score += maxNearby;
                count++;
            }
        }

        return count > 0 ? score / count : 0;
    }

    /**
     * Applique la différenciation saisonnière si nécessaire
     */
    private static SeasonalDifferenceResult seasonalDifferenceIfNeeded(double[] data, int period, boolean verbose) {
        double[] currentData = data.clone();
        int D = 0;

        for (int i = 0; i < 2; i++) { // Maximum 2 différenciations saisonnières
            double[] acf = calculateACF(currentData, Math.min(period * 4, currentData.length / 4));

            // Vérifier si la composante saisonnière est forte
            boolean hasSeasonalComponent = false;
            if (acf.length > period) {
                double seasonalACF = Math.abs(acf[period]);
                if (seasonalACF > 0.7) {
                    hasSeasonalComponent = true;
                }
            }

            if (!hasSeasonalComponent) {
                break;
            }

            if (verbose) {
                System.out.println("Différenciation saisonnière " + (D + 1) + " appliquée (période=" + period + ")");
            }

            currentData = seasonalDifference(currentData, period);
            D++;

            if (currentData.length < period * 2) {
                break; // Pas assez de données
            }
        }

        if (verbose) {
            System.out.println(" Ordre de différenciation saisonnière D = " + D);
        }

        return new SeasonalDifferenceResult(currentData, D);
    }

    /**
     * Applique une différenciation saisonnière
     */
    private static double[] seasonalDifference(double[] data, int period) {
        double[] result = new double[data.length - period];
        for (int i = 0; i < result.length; i++) {
            result[i] = data[i + period] - data[i];
        }
        return result;
    }

    /**
     * Applique la différenciation non-saisonnière si nécessaire
     */
    private static DifferencingResult differenceIfNeeded(double[] data, boolean verbose) {
        double[] currentData = data.clone();
        int d = 0;

        for (int i = 0; i < 2; i++) {
            int maxLag = Math.min(20, currentData.length / 4);
            double[] acf = calculateACF(currentData, maxLag);

            if (isStationary(currentData, acf)) {
                if (verbose) {
                    System.out.println("Série stationnaire après " + d + " différenciation(s)");
                }
                break;
            } else {
                if (verbose) {
                    System.out.println("Différenciation " + (d + 1) + " appliquée");
                }
                currentData = difference(currentData);
                d++;

                if (currentData.length < 10) {
                    break;
                }
            }
        }

        if (verbose) {
            System.out.println("Ordre de différenciation non-saisonnière d = " + d);
        }

        return new DifferencingResult(currentData, d);
    }

    /**
     * Différenciation simple
     */
    private static double[] difference(double[] data) {
        double[] diff = new double[data.length - 1];
        for (int i = 0; i < diff.length; i++) {
            diff[i] = data[i + 1] - data[i];
        }
        return diff;
    }

    /**
     * Test de stationnarité
     */
    private static boolean isStationary(double[] data, double[] acf) {
        boolean acfTest = testACFDecay(acf);
        boolean varianceTest = testVarianceStability(data);
        boolean meanTest = testMeanStability(data);

        int passedTests = (acfTest ? 1 : 0) + (varianceTest ? 1 : 0) + (meanTest ? 1 : 0);
        return passedTests >= 2;
    }

    private static boolean testACFDecay(double[] acf) {
        if (acf.length < 5) return true;
        if (acf[1] > 0.95) return false;

        double avgDecay = 0;
        int lags = Math.min(10, acf.length - 1);
        for (int i = 1; i < lags; i++) {
            avgDecay += Math.abs(acf[i] - acf[i + 1]);
        }
        avgDecay /= lags;

        return avgDecay >= 0.05;
    }

    private static boolean testVarianceStability(double[] data) {
        if (data.length < 30) return true;

        int numSegments = 3;
        int segmentSize = data.length / numSegments;
        double[] variances = new double[numSegments];

        for (int i = 0; i < numSegments; i++) {
            int start = i * segmentSize;
            int end = (i == numSegments - 1) ? data.length : (i + 1) * segmentSize;
            double[] segment = Arrays.copyOfRange(data, start, end);
            variances[i] = calculateVariance(segment);
        }

        double minVar = Arrays.stream(variances).min().orElse(1.0);
        double maxVar = Arrays.stream(variances).max().orElse(1.0);

        return (maxVar / (minVar + 0.0001)) < 3.0;
    }

    private static boolean testMeanStability(double[] data) {
        if (data.length < 30) return true;

        int numSegments = 3;
        int segmentSize = data.length / numSegments;
        double[] means = new double[numSegments];

        for (int i = 0; i < numSegments; i++) {
            int start = i * segmentSize;
            int end = (i == numSegments - 1) ? data.length : (i + 1) * segmentSize;
            double[] segment = Arrays.copyOfRange(data, start, end);
            means[i] = calculateMean(segment);
        }

        double meanOfMeans = Arrays.stream(means).average().orElse(0.0);
        double stdOfMeans = 0;
        for (double mean : means) {
            stdOfMeans += Math.pow(mean - meanOfMeans, 2);
        }
        stdOfMeans = Math.sqrt(stdOfMeans / numSegments);

        double globalStd = Math.sqrt(calculateVariance(data));
        return stdOfMeans < 0.2 * globalStd;
    }

    /**
     * Identifie p et q (non-saisonniers)
     */
    private static IdentificationResult identifyARMA(double[] acf, double[] pacf, int n, int seasonalPeriod, boolean verbose) {
        double confidenceBound = 1.96 / Math.sqrt(n);

        // Ignorer les lags saisonniers pour l'identification non-saisonnière
        int maxNonSeasonalLag = seasonalPeriod > 1 ? Math.min(seasonalPeriod - 1, 10) : 10;

        int p = findCutoffLag(pacf, confidenceBound, maxNonSeasonalLag);
        int q = findCutoffLag(acf, confidenceBound, maxNonSeasonalLag);

        if (verbose) {
            System.out.println("   Paramètres non-saisonniers :");
            System.out.println("      p (AR) = " + p + (p == -1 ? " (décroissance exponentielle)" : ""));
            System.out.println("      q (MA) = " + q + (q == -1 ? " (décroissance exponentielle)" : ""));
        }

        // Si décroissance exponentielle, choisir des valeurs par défaut
        if (p == -1) p = 1;
        if (q == -1) q = 1;

        return new IdentificationResult(p, q);
    }

    /**
     * Identifie P et Q (saisonniers)
     */
    private static IdentificationResult identifySeasonalARMA(double[] acf, double[] pacf, int n, int period, boolean verbose) {
        double confidenceBound = 1.96 / Math.sqrt(n);

        // Examiner uniquement les lags saisonniers : s, 2s, 3s, ...
        int maxSeasonalLags = 3; // Examiner jusqu'à 3 périodes saisonnières

        int P = 0;
        int Q = 0;

        // Vérifier PACF aux lags saisonniers pour P
        for (int i = 1; i <= maxSeasonalLags; i++) {
            int lag = i * period;
            if (lag < pacf.length && Math.abs(pacf[lag]) > confidenceBound) {
                P = i;
            } else {
                break;
            }
        }

        // Vérifier ACF aux lags saisonniers pour Q
        for (int i = 1; i <= maxSeasonalLags; i++) {
            int lag = i * period;
            if (lag < acf.length && Math.abs(acf[lag]) > confidenceBound) {
                Q = i;
            } else {
                break;
            }
        }

        // Limiter à des valeurs raisonnables
        P = Math.min(P, 2);
        Q = Math.min(Q, 2);

        if (verbose) {
            System.out.println("   Paramètres saisonniers :");
            System.out.println("      P (AR saisonnier) = " + P);
            System.out.println("      Q (MA saisonnier) = " + Q);
        }

        return new IdentificationResult(P, Q);
    }

    /**
     * Trouve le lag de coupure
     */
    private static int findCutoffLag(double[] correlations, double confidenceBound, int maxLag) {
        int lastSignificant = 0;
        maxLag = Math.min(maxLag, correlations.length - 1);

        for (int i = 1; i <= maxLag; i++) {
            if (Math.abs(correlations[i]) > confidenceBound) {
                lastSignificant = i;
            }
        }

        // Vérifier s'il y a une vraie coupure
        if (lastSignificant == 0) return 0;

        int consecutiveNonSig = 0;
        for (int i = lastSignificant + 1; i < Math.min(lastSignificant + 4, correlations.length); i++) {
            if (Math.abs(correlations[i]) <= confidenceBound) {
                consecutiveNonSig++;
            } else {
                return -1; // Décroissance exponentielle
            }
        }

        return consecutiveNonSig >= 2 ? lastSignificant : -1;
    }

    /**
     * Calcule ACF
     */
    private static double[] calculateACF(double[] data, int maxLag) {
        double mean = calculateMean(data);
        double variance = calculateVariance(data);

        double[] acf = new double[maxLag + 1];
        acf[0] = 1.0;

        for (int k = 1; k <= maxLag; k++) {
            double covariance = 0.0;
            for (int t = 0; t < data.length - k; t++) {
                covariance += (data[t] - mean) * (data[t + k] - mean);
            }
            covariance /= data.length;
            acf[k] = covariance / variance;
        }

        return acf;
    }

    /**
     * Calcule PACF (algorithme de Durbin-Levinson)
     */
    private static double[] calculatePACF(double[] acf, int maxLag) {
        double[] pacf = new double[maxLag + 1];
        pacf[0] = 1.0;

        if (maxLag == 0) return pacf;

        pacf[1] = acf[1];

        double[][] phi = new double[maxLag][maxLag];
        phi[0][0] = acf[1];

        for (int k = 2; k <= maxLag; k++) {
            double numerator = acf[k];
            double denominator = 1.0;

            for (int j = 1; j < k; j++) {
                numerator -= phi[k - 2][j - 1] * acf[k - j];
                denominator -= phi[k - 2][j - 1] * acf[j];
            }

            pacf[k] = numerator / denominator;

            for (int j = 1; j < k; j++) {
                phi[k - 1][j - 1] = phi[k - 2][j - 1] - pacf[k] * phi[k - 2][k - j - 1];
            }
            phi[k - 1][k - 1] = pacf[k];
        }

        return pacf;
    }

    private static double calculateMean(double[] data) {
        return Arrays.stream(data).average().orElse(0.0);
    }

    private static double calculateVariance(double[] data) {
        double mean = calculateMean(data);
        double variance = 0.0;
        for (double value : data) {
            variance += Math.pow(value - mean, 2);
        }
        return variance / data.length;
    }

    /**
     * Calcule un score de confiance pour les paramètres identifiés
     */
    private static double calculateConfidence(double[] acf, double[] pacf, int n, SARIMAParameters params) {
        double score = 0.5; // Score de base

        double bound = 1.96 / Math.sqrt(n);

        // Augmenter la confiance si les paramètres sont clairs
        if (params.p > 0 && params.p < pacf.length && Math.abs(pacf[params.p]) > 2 * bound) {
            score += 0.15;
        }
        if (params.q > 0 && params.q < acf.length && Math.abs(acf[params.q]) > 2 * bound) {
            score += 0.15;
        }

        // Bonus pour saisonnalité détectée
        if (params.s > 1) {
            score += 0.1;
        }

        // Pénalité si trop de paramètres
        int totalParams = params.p + params.q + params.P + params.Q;
        if (totalParams > 4) {
            score -= 0.1;
        }

        return Math.max(0.0, Math.min(1.0, score));
    }

    // Classes auxiliaires
    private static class SeasonalPeak {
        int lag;
        double strength;
        double regularity;

        SeasonalPeak(int lag, double strength, double regularity) {
            this.lag = lag;
            this.strength = strength;
            this.regularity = regularity;
        }

        double getScore() {
            return strength * (0.6 + 0.4 * regularity);
        }
    }

    private static class DifferencingResult {
        double[] data;
        int order;

        DifferencingResult(double[] data, int order) {
            this.data = data;
            this.order = order;
        }
    }

    private static class SeasonalDifferenceResult {
        double[] data;
        int order;

        SeasonalDifferenceResult(double[] data, int order) {
            this.data = data;
            this.order = order;
        }
    }

    private static class IdentificationResult {
        int p;
        int q;

        IdentificationResult(int p, int q) {
            this.p = p;
            this.q = q;
        }
    }

    public void displayParameters(double[] data){
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("EXEMPLE 1 : Calcul des paramètres ARIMA");
        System.out.println("═══════════════════════════════════════════════════════\n");
        SARIMAParameters params1 = analyzeSARIMA(data, true);

    }

}