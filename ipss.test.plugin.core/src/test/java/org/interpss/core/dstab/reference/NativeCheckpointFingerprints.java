package org.interpss.core.dstab.reference;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Fixed SHA-256 fingerprints of the native checkpoints in commit dcea9ab30.
 * Bytes are UTF-8 CSV lines joined with LF, without a final newline.
 * These fingerprints protect the embedded checkpoints; they do not replace
 * the private simulator manifests or establish simulator provenance.
 */
final class NativeCheckpointFingerprints {
    private NativeCheckpointFingerprints() {}

    private static final Map<String, String> EXPECTED = Map.ofEntries(
            Map.entry("fivebus-svsmo1t2/psse.csv", "1325eb26eb3e7e23197d7878904f09eda4cd6fffa20bcd5fe8d0b549b3ef432f"),
            Map.entry("ieee9-generator-trip-relays/psse.csv", "12a17ec7da73c0ff0b8faa7420ed362621551e8a9f98f0f12d2c6c62b03162c9"),
            Map.entry("ieee9-ieelar/psse.csv", "3784b5a7a7f2a3f018e89fb00006955d8f607e69d19f4404c154a91ad9ef41c9"),
            Map.entry("ieee9-load-shedding-relays/psse.csv", "393a5d0f9d97357a94bc50fd1db79ab3aead4af4d5b9b40d1726783e380b9bf8"),
            Map.entry("ieee9-perc1-multiramp/psse.csv", "229840076114d0a6d6ea9a4ba627c42f885397444045473affb1c1f75c9d594b"),
            Map.entry("ieee9-perc1/psse.csv", "d9cfdfd71114bccb9d7f50ddf31985bac47a271f3e68fc85b3fc94445bafe890"),
            Map.entry("reecc1-bus1062/psse.csv", "aa46a9f6ca8ce3e248f073d51fa7c8059a72b9eff46e8d2eb1a644ce5fd6f205"),
            Map.entry("renewable-aggregate-5/psse.csv", "a759de925402f3a623f0edfb1a763f6919f6d1066d22bf4f783f30bf7c3155ba"),
            Map.entry("renewable-bus1062-flat/psse.csv", "3ed5df5764a69ed81698a03ba9ee2ac1737b798304f6c6606568e753aba33b5c"),
            Map.entry("renewable-bus1062/psse.csv", "b107b10469e67d4edd412c89cade2de2625aceddd2ff787b442e7c408ac291ea"),
            Map.entry("smib-ac8cu1/psse.csv", "85bc55e07eb6114588dd253dc29cc539d633a27834487c46cf417836f4b63135"),
            Map.entry("smib-cimtr4/psse.csv", "f1866ab42a6cf79b91a9f2fc92e640c029de4b87e0c1f563c2b09d5b53cee143"),
            Map.entry("smib-csvgn5/psse.csv", "fdeaf0555ec81292fb3367a4adefa418c000ac6eaad011d0140275ec2d07ae06"),
            Map.entry("smib-dc4cu1/psse.csv", "f7c872dd987696fcca6def0fc018bd8eb65ce6c58b211d209fe9a5228e4ef245"),
            Map.entry("smib-dera1/psse.csv", "463e9817a64dcbc853ab93710a3be61af195fa09595088ea34db93d5767edcd6"),
            Map.entry("smib-genqecu/psse.csv", "dd9c349b6fa25d306935cc80c44f7a5416d82c64bf332a7abec536d9ad99dfbc"),
            Map.entry("smib-genqej/psse.csv", "66e09066c66331793ec24008b735dd647290387a8bb69d65ce13164f40158300"),
            Map.entry("smib-genrou-bbsex1/psse.csv", "32fecad6691acb86e5cad3c3283b53e8927b83939e0cf2e44e4cdbd13561f38b"),
            Map.entry("smib-genrou-esst1a-ieeest/psse.csv", "aeccc535962f15b7a420d0ee64e3dbaf5c0d399ae906ee977f36aa69867c76ae"),
            Map.entry("smib-genrou-esst1a-pss2c/psse.csv", "4859920fff688a450f27da7d00480208445c419140a3e7f6585075efaa27c566"),
            Map.entry("smib-genrou-esst1a-pss3b/psse.csv", "d0eb1435b292cb5b3e6446b2cb66df9a66318e72e3121f7993d1b69d57fdd579"),
            Map.entry("smib-genrou-esst1a-pss3c/psse.csv", "eef6f6ee714b77301aa4f5458d8434519faf2153a81a11d762ab696b85ebd51c"),
            Map.entry("smib-genrou-esst1a-pss4b/psse.csv", "34b6371b0e377dc560b72ffb087f2c6e64e90742a65a776da9f4cbde32ec8633"),
            Map.entry("smib-genrou-esst1a-pss6c/psse.csv", "a7fc4dea02ce4054ff7cf3256d12d7312e3f397d9a8d6e6f892d7c9cfd5118e2"),
            Map.entry("smib-genrou-esst1a-pss7c/psse.csv", "569850aed6aefb9f3199b058d343d09adf8d11aa08759b4a3f9d28081c16fbf9"),
            Map.entry("smib-genrou-esst1a-st2cut/psse.csv", "0de1cba6d5ebeb35a72243d4303436adbe354554b2c489d8578675617e487947"),
            Map.entry("smib-genrou-exdc2/psse.csv", "19cdc9984a804fe24e7d658e6f44bbca1e8fcbce1196692a53cf2d2e58ee67fe"),
            Map.entry("smib-genrou-exeli/psse.csv", "8fc703fd46c800d54f7b8ffacc98da20bb62d5f0f58c74e4001639a9e7476939"),
            Map.entry("smib-genrou-exst2/psse.csv", "d72ac6abdde6d0d01317c514f8f2a1e152b743d8685eaeb58619eb0fb84d8060"),
            Map.entry("smib-genrou-ggov1-trip/psse.csv", "849b542cd7b4d0db5dd5c4dd28cf53c28a9572ee546f162998a6b88e09268d8e"),
            Map.entry("smib-genrou-ieeeg1d/psse.csv", "9082f2322fe13b70bfde14025386b80fa33c7ddaa592626d9095e6f703763e90"),
            Map.entry("smib-genrou-ieeet4/psse.csv", "d00b8fc193b8a93a32f3cfcb7621463814f421353c471d79a43f619b002261c1"),
            Map.entry("smib-genrou-ieeex1/psse.csv", "aec40c3431c05a2f4707aeaa68251af2c01641d1202d43d3c41622d3bb3929b8"),
            Map.entry("smib-genrou-ieeex2/psse.csv", "51ebed8829b11c2975131cac1254b34f7738edaeeb3b6eaec2f2176999854b4d"),
            Map.entry("smib-genrou-pidgov/psse.csv", "096725eed20567e6b94529b52a8b855e80ab018dccf3d01e30ebfac9c2d89b09"),
            Map.entry("smib-genrou-scrx/psse.csv", "a135e11acf11694e043a05cedd9b785412dfbabc844133ef072764599e870312"),
            Map.entry("smib-genrou-st10c/psse.csv", "c09b3459e8a6590a42a09c7f792c158e3de0f75ea93d386df85a227cf0c8b996"),
            Map.entry("smib-genrou-st1c-oel2cu1/native.csv", "4874dcf3dcefd8db9db62fcd58cdd21b0158f119acbd66108de8b405d5989b9a"),
            Map.entry("smib-genrou-st1c-uel1/psse.csv", "9152de81d025e86cffc0dead1909c84a10e7a9ae3f77aeda9b0a01332955931d"),
            Map.entry("smib-genrou-st1c-uel2cu1/native.csv", "48b52b9cf2d40ff80c55b192fbe5d16135ccc729470b334ad004f97aab05bc49"),
            Map.entry("smib-genrou-st6c/psse.csv", "a559057e19abca26135f41195f06e551b4599691fd05ad5faa695b88e4755bd9"),
            Map.entry("smib-genrou-st7b/psse.csv", "040e2103df3b413b31e28dea5857bc358c6d922cce2f6d2bcd8f3f8931c7bc9e"),
            Map.entry("smib-genrou-st7c/psse.csv", "69caaee7aeec29a1322a651cc5ba30c657be7e5b67b627958747782faadadd6b"),
            Map.entry("smib-genrou-st8c/psse.csv", "a280bada23547b554030eabbceae23244d2dc25664c15bc8b9531551f3539b69"),
            Map.entry("smib-genrou-st9c/psse.csv", "04e7dfd2de55801dfa328d451cc012c677dc5528ba326a691434191fc40d12a1"),
            Map.entry("smib-gentpj1/psse.csv", "d184c1d619fef73cb600ec1408044ef241858abe5ee5edc5a09ff1d653723d57"),
            Map.entry("smib-gentra/psse.csv", "1968b0b5fc4f6efe574c4235ea9848ea5b9003d2d8f0fa98b7cf411c5f83a110"),
            Map.entry("smib-gewtgcu1/psse.csv", "5d0358be8e239aeb530f72f9e5f387942821586fb32fd5155e6cf21e3befbd1d"),
            Map.entry("smib-ieeevc/psse.csv", "9998c1bb4d05c1b773c74d35af3cde1446e6151f223beb903ae897afc713c841"),
            Map.entry("smib-regcb1/psse.csv", "76579720250d91229586f64bd326d65c481de1cf20e37315bad5b9b271c08012"),
            Map.entry("smib-st4cu1/psse.csv", "89f76e136397353f127ea583b1edc09993a8b2f2765e9de9f8e1b2f9d7fea7be"),
            Map.entry("smib-wt12a1/psse.csv", "5f503d0c044056f396031e938d7efc51cb38a13d05f4122789431f22be24072a"),
            Map.entry("smib-wt12t1/psse.csv", "61b6ed8e4e958805b6e0062202b40408c42b237df774a309841642564bf752bd"),
            Map.entry("smib-wt1g1/psse.csv", "9d410aa3f63a6c7b1f43aec881eea62820f84383f06b57606c652eb68a6d9312"),
            Map.entry("smib-wt2e1/psse.csv", "4518a7421bf41aeffab5f2d4eb202bf49a99cd517dddd2e9784ce78491c6cfc6"),
            Map.entry("smib-wt2g1/psse.csv", "fbbd099cc79b16e5a1bd0650cec3ccd58138d503eff8c8eb22903765d07a99bc"),
            Map.entry("smib-wt3e1/psse.csv", "031dc776ab5f4c602c7e9aefcf883fe54fb897d027d8cfa2dac85dafe0f1f59f"),
            Map.entry("smib-wt3g1/psse.csv", "36188890e2a420d7265638cbc2144ac381961a2efd9c5f76c2a7d04937821d04"),
            Map.entry("smib-wt3g2/psse.csv", "2132dc1998292361e28680c8744a184dc52f01df35364274e29b0bae014e49b3"),
            Map.entry("smib-wt3p1/psse.csv", "8dc2cd0824ed4f7d922cbde55e63c169c06971152cd4ff9d69f62e99e5a5ef6e"),
            Map.entry("smib-wt3t1/psse.csv", "4d728539a7ccc34d69fe3d03695c5085e05a6c550ebee6b6ab975667600a90b0"),
            Map.entry("smib-wt4e1/psse.csv", "f34b1a8c7fddef491523189135f1b240a24da7718b7f33bfd227ac73485adbb2"),
            Map.entry("smib-wt4g1/psse.csv", "9ec83e916006082fb151e7d4e1cc457976a30b8ea8873929451f9e014f709b77")
    );

    static Set<String> paths() {
        return EXPECTED.keySet();
    }

    static String expectedSha256(Path path) {
        String normalized = path.toString().replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        int parentSeparator = normalized.lastIndexOf('/', separator - 1);
        String key = parentSeparator < 0 ? normalized : normalized.substring(parentSeparator + 1);
        String expected = EXPECTED.get(key);
        if (expected == null) {
            throw new IllegalArgumentException("No fixed native checkpoint fingerprint for " + path);
        }
        return expected;
    }

    static String expectedManifestSha256(Path path) {
        String normalized = path.toString().replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        String parent = separator < 0 ? "" : normalized.substring(0, separator);
        String prefix = parent.substring(parent.lastIndexOf('/') + 1) + "/";
        return EXPECTED.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No fixed native checkpoint fingerprint for " + path));
    }
}
