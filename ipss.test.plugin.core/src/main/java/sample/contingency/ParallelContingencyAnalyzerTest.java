package sample.contingency;

import org.interpss.plugin.contingency.ParallelContingencyAnalyzer;

/**
 * Simple test to verify ParallelContingencyAnalyzer compiles and basic functionality works.
 */
public class ParallelContingencyAnalyzerTest {
    
    public static void main(String[] args) {
        try {
            // Initialize InterPSS
            //IpssCorePlugin.init();
            //IpssLogger.getLogger().setLevel(Level.INFO);
            
            System.out.println("=== ParallelContingencyAnalyzer Test ===");
            
            // Test configuration creation
            ParallelContingencyAnalyzer.ContingencyConfig config = 
                ParallelContingencyAnalyzer.createDefaultConfig();
            
            System.out.println("Default configuration created successfully:");
            System.out.println("  Max Iterations: " + config.getMaxIterations());
            System.out.println("  Tolerance: " + config.getTolerance());
            System.out.println("  Load Flow Method: " + config.getLfMethod());
            System.out.println("  Non-divergent: " + config.isNonDivergent());
            System.out.println("  Turn off island bus: " + config.isTurnOffIslandBus());
            System.out.println("  Auto turn line to xfr: " + config.isAutoTurnLine2Xfr());
            
            // Test configuration modification
            config.setMaxIterations(100);
            config.setTolerance(0.001);
            
            System.out.println("\nModified configuration:");
            System.out.println("  Max Iterations: " + config.getMaxIterations());
            System.out.println("  Tolerance: " + config.getTolerance());
            
            System.out.println("\n✓ ParallelContingencyAnalyzer basic functionality test passed!");
            
        } catch (Exception e) {
            System.err.println("✗ Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
