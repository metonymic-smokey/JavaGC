package at.jku.anttracks.heapsizeselector;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.*;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.function.LongSupplier;

public class Main {

    public static void main(String[] args) throws FileNotFoundException, IOException {
        int index = 0;
        String application = args[index++];
        String file = index < args.length ? args[index++] : null;
        main(application, file != null ? new File(file) : null);
    }

    private static void main(String application, File file) throws FileNotFoundException, IOException {
        String size;
        if (file != null && file.exists() && file.canRead()) {
            size = readHeapSize(file);
        } else {
            size = askUser(application, file);
        }
        System.out.print(size);
        System.exit(0);
    }

    private static String readHeapSize(File file) throws IOException, FileNotFoundException {
        try (BufferedReader in = new BufferedReader(new FileReader(file))) {
            StringBuffer content = new StringBuffer();
            for (String line = in.readLine(); line != null; line = in.readLine()) {
                content.append(line);
                content.append('\n');
            }
            return content.toString().trim();
        }
    }

    private static void writeHeapSize(File file, String size) {
        try (BufferedWriter out = new BufferedWriter(new FileWriter(file))) {
            out.write(size);
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
    }

    private static String askUser(String application, File file) {
        JFrame frame = new JFrame();

        JLabel description = new JLabel("Please select an appropriate heap size for " + application + ".");
        description.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        description.setFont(description.getFont().deriveFont(Font.PLAIN | Font.ITALIC));

        MemoryComponent memory = new MemoryComponent(MemoryInfo.get());
        JLabel total = new JLabel();
        JLabel available = new JLabel();
        JLabel usage = new JLabel();

        Font font = total.getFont().deriveFont(Font.PLAIN);
        total.setFont(font);
        available.setFont(font);
        //usage.setFont(font);

        JPanel info = new JPanel();
        info.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        info.setLayout(new GridLayout(1, 6));
        info.add(new JLabel("Total Memory:"));
        info.add(total);
        info.add(new JLabel("Available Memory:"));
        info.add(available);
        info.add(new JLabel("Used Memory:"));
        info.add(usage);

        JSlider slider = new JSlider(0, 100);
        slider.setMajorTickSpacing(25);
        slider.setMinorTickSpacing(10);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        slider.setPaintTrack(true);
        Dictionary<Integer, JLabel> sliderLabels = new Hashtable<Integer, JLabel>();
        for (int i = 0; i <= 100; i = i + 25) {
            sliderLabels.put(i, new JLabel(i + "%"));
        }
        slider.setLabelTable(sliderLabels);
        JComboBox<SelectionMode> box = new JComboBox<SelectionMode>(SelectionMode.values());
        box.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));

        JPanel config = new JPanel();
        config.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        config.setLayout(new BorderLayout());
        config.add(slider, BorderLayout.CENTER);
        config.add(box, BorderLayout.EAST);

        JPanel main = new JPanel();
        main.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        main.setLayout(new BorderLayout());
        main.add(info, BorderLayout.NORTH);
        main.add(memory, BorderLayout.CENTER);
        main.add(config, BorderLayout.SOUTH);

        JButton ok = new JButton("OK");
        ok.addActionListener(e -> {
            frame.setVisible(false);
            synchronized (frame) {
                frame.notifyAll();
            }
        });
        JCheckBox remember = new JCheckBox();
        remember.setSelected(false);
        remember.setText("Remember my decision.");
        JPanel buttons = new JPanel();
        buttons.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        //buttons.setLayout(new BorderLayout());
        //buttons.add(new JLabel(), BorderLayout.CENTER);
        buttons.add(ok, BorderLayout.CENTER);
        if (file != null) {
            buttons.add(remember, BorderLayout.EAST);
        }

        frame.setTitle(application);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setResizable(false);
        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(description, BorderLayout.NORTH);
        frame.getContentPane().add(main, BorderLayout.CENTER);
        frame.getContentPane().add(buttons, BorderLayout.SOUTH);
        frame.pack();
        frame.setVisible(true);

        {
            GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            frame.setLocation((gd.getDisplayMode().getWidth() - frame.getWidth()) / 2, (gd.getDisplayMode().getHeight() - frame.getHeight()) / 2);
        }

        frame.addWindowListener(new WindowListener() {
            public void windowOpened(WindowEvent e) {}

            public void windowClosing(WindowEvent e) {}

            public void windowClosed(WindowEvent e) {
                System.exit(1);
            }

            public void windowIconified(WindowEvent e) {}

            public void windowDeiconified(WindowEvent e) {}

            public void windowActivated(WindowEvent e) {}

            public void windowDeactivated(WindowEvent e) {}
        });

        Thread worker = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                MemoryInfo data = MemoryInfo.get();
                memory.setMemory(data);
                total.setText(MemoryInfo.toString(data.total));
                available.setText(MemoryInfo.toString(data.total * data.available));
                try {
                    Thread.sleep(1000 * 1);
                } catch (Exception e1) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        worker.setDaemon(true);

        LongSupplier getBytes = () -> ((SelectionMode) box.getSelectedItem()).translate(memory.getMemory(), 1.0 * slider.getValue() / 100);

        ActionListener updater = e -> {
            long bytes = getBytes.getAsLong();
            memory.setUsage(bytes);
            usage.setText(MemoryInfo.toString(bytes));
        };
        slider.addChangeListener(e -> updater.actionPerformed(null));
        box.addItemListener(e -> updater.actionPerformed(null));

        slider.setValue(75);
        box.setSelectedItem(SelectionMode.AVAILABLE);
        worker.start();

        synchronized (frame) {
            while (frame.isVisible()) {
                try {
                    frame.wait();
                } catch (InterruptedException e1) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        frame.dispose();
        String size = Long.toString(getBytes.getAsLong());
        if (file != null && remember.isSelected()) {
            writeHeapSize(file, size);
        }
        return size;
    }

    private static enum SelectionMode {
        TOTAL,
        AVAILABLE;

        public long translate(MemoryInfo data, double ratio) {
            switch (this) {
                case TOTAL:
                    return (long) (data.total * ratio);
                case AVAILABLE:
                    return (long) (data.total * data.available * ratio);
                default:
                    return 0;
            }
        }

        @Override
        public String toString() {
            return super.toString().toLowerCase().replace(' ', ' ');
        }
    }

}
