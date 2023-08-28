package de.friedrichs.vcard;

import de.friedrichs.vcard.google.DiffMatchPatch;
import ezvcard.Ezvcard;
import ezvcard.VCard;
import ezvcard.VCardVersion;
import ezvcard.parameter.EmailType;
import ezvcard.parameter.TelephoneType;
import ezvcard.property.*;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "vcard")
@Slf4j
public class IOController {

    @Setter @Getter
    private String input;
    @Setter @Getter
    private String output;
    private Path outputPath;

    private final CardComparator cardComparator = new CardComparator();
    private final String key = "%d|%d";
    private final DiffMatchPatch diffMatchPatch = new DiffMatchPatch();

    @Getter
    @Setter
    private List<VCard> vCards;

    @PostConstruct
    public void loadCards() throws IOException {
      if (input == null || input.isEmpty()){
          return;
      }

      log.info("Lade vCards von {} ein", input);
      setVCards(Ezvcard.parse(Path.of(input)).all());

      log.info("{} vCards geladen", this.vCards.size());
    }

    public void counter() throws IOException {
        List<VCard> list = this.vCards.stream()
                .parallel().filter(c -> c.getBirthday() != null || !c.getBirthdays().isEmpty())
                .toList();
        log.info("{} Cards mit Geburtstagen gefunden", list.size());
        Ezvcard.write(list)
                .version(VCardVersion.V3_0)
                .prodId(false)
                .go(Path.of(this.output));
    }

    public void diff() throws IOException {
        List<VCard> list = this.vCards.stream()
                .parallel().filter(c -> c.getBirthday() != null || !c.getBirthdays().isEmpty())
                .toList();
        Map<StructuredName, List<VCard>> map = new HashMap<>();
        list.forEach(c -> {
            if (map.containsKey(c.getStructuredName())){
                map.get(c.getStructuredName()).add(c);
            }else {
                List<VCard> cards = new ArrayList<>();
                cards.add(c);
                map.put(c.getStructuredName(), cards);
            }
        });
        List<VCard> out = new ArrayList<>();
        map.forEach((k,v) -> {
            if (v.size() == 1){
                out.add(v.get(0));
            }else {
                out.add(merge(v));
            }
        });

        log.info("{} Cards mit Geburtstagen gefunden", out.size());
        Ezvcard.write(out)
                .version(VCardVersion.V3_0)
                .prodId(false)
                .go(Path.of(this.output));
    }

    private VCard merge(List<VCard> v) {
        if (v.size() != 2){
            log.warn("kann Liste mit {} vcards nicht verarbeiten --> {}", v.size(), v.get(0).getStructuredName());
            return null;
        }
        VCard v1 = v.get(0);
        VCard v2 = v.get(1);
        VCard result = new VCard();

        result.setStructuredName(v1.getStructuredName().copy());
        List<Class> simpleProps = List.of(FormattedName.class, Photo.class, Birthday.class, Address.class);
        simpleProps.parallelStream().forEach( p -> {
            if (!mergeProperties(p, v1, v2, result)){
                aggregateProperties(p, v1, v2, result);
            }
        });
        if (!mergeProperties(Telephone.class, v1, v2, result)){
            mergeTelephone(v1, v2, result);
        }
        if (!mergeProperties(Email.class, v1, v2, result)){
            mergeEmail(v1, v2, result);
        }

        log.info("Merge {}", v1.getStructuredName());

        return result;
    }

    private void mergeEmail(VCard v1, VCard v2, VCard result) {
        Map<String, Email> emailMap = new HashMap<>();
        v1.getEmails().forEach(email -> {
            emailMap.put(email.getValue(), removePref(email));
        });
        v2.getEmails().forEach(email -> {
            emailMap.put(email.getValue(), removePref(email));
        });
        emailMap.values().forEach( e -> result.addEmail(e));
    }

    private String normaliseTelephone(Telephone number){
        String normalised = number.getText().trim();
        String international =
                (normalised.startsWith("+"))
                ? normalised
                : "+49".concat(normalised.substring(1));
        String suffix = (number.getTypes().isEmpty())
                ? "EMPTY"
                : number.getTypes().get(0).toString();
        return international.concat("_").concat(suffix);
    }

    private Email removePref(Email email){
        Email e = new Email(email.getValue());
        email.getTypes().forEach( t -> {
            if (t != EmailType.PREF){
                e.getTypes().add(t);
            }
        });
        if (email.getAltId() != null && !email.getAltId().isEmpty()){
            e.setAltId(email.getAltId());
        }
        return e;
    }

    private Telephone removePref(Telephone tp){
        Telephone telephone = new Telephone(tp.getText());
        tp.getTypes().forEach( t -> {
            if (t != TelephoneType.PREF){
                telephone.getTypes().add(t);
            }
        });
        if (tp.getAltId() != null && !tp.getAltId().isEmpty()){
            telephone.setAltId(tp.getAltId());
        }
        return telephone;
    }

    private void mergeTelephone(VCard v1, VCard v2, VCard result) {
        Map<String, Telephone> telephoneMap = new HashMap<>();
        v1.getTelephoneNumbers().forEach( t -> {
            Telephone telephone = removePref(t);
            telephoneMap.put(normaliseTelephone(telephone), telephone);
        });
        v2.getTelephoneNumbers().forEach( t -> {
            Telephone telephone = removePref(t);
            telephoneMap.put(normaliseTelephone(telephone), telephone);
        });
        telephoneMap.values().forEach( v -> result.addTelephoneNumber(v));
    }

    private void mergeFormattedName(VCard v1, VCard v2, VCard result) {
        List<FormattedName> formattedNames1 = new ArrayList<>();
        List<FormattedName> formattedNames2 = new ArrayList<>();
        v1.getFormattedNames().forEach( fn -> formattedNames1.add(fn.copy()));
        v2.getFormattedNames().forEach( fn -> formattedNames2.add(fn.copy()));

        formattedNames1.forEach( p -> {
            if (formattedNames2.contains(p)){
                formattedNames2.remove(p);
            }
            result.addFormattedName(p.copy());
        });
        formattedNames2.forEach( p -> result.addFormattedName(p.copy()));
    }

    private <T extends VCardProperty> boolean mergeProperties(Class<T> clazz, VCard v1, VCard v2, VCard result){
        if (v1.getProperties(clazz).isEmpty() && v2.getProperties(clazz).isEmpty()){
            return true;
        }
        if (!v1.getProperties(clazz).isEmpty() && v2.getProperties(clazz).isEmpty()){
            v1.getProperties(clazz).forEach( p -> result.getProperties(clazz).add((T) p.copy()));
            return true;
        }
        if (v1.getProperties(clazz).isEmpty() && !v2.getProperties(clazz).isEmpty()){
            v2.getProperties(clazz).forEach( p -> result.getProperties(clazz).add((T) p.copy()));
            return true;
        }
        return false;
    }

    private <T extends VCardProperty> void aggregateProperties(Class<T> clazz, VCard v1, VCard v2, VCard result){
        List<T> props1 = new ArrayList<>();
        List<T> props2 = new ArrayList<>();

        v1.getProperties(clazz).forEach( p -> props1.add((T) p.copy()));
        v2.getProperties(clazz).forEach( p -> props2.add((T) p.copy()));

        props1.forEach( p -> {
            if (props2.contains(p)){
                props2.remove(p);
            }
            result.getProperties(clazz).add((T) p.copy());
        });
        props2.forEach( p -> result.getProperties(clazz).add((T) p.copy()));
    }



    @PostConstruct
    public void createOutPutFile() throws IOException {
        if (output == null || output.isEmpty()){
            return; //TODO ggf. Dummy oder TempFile
        }

        this.outputPath = Path.of(this.output);
        if (!Files.exists(this.outputPath)){
            Files.createFile(this.outputPath);
        }
    }

    public void removeProdId() throws IOException {
        this.vCards.stream().parallel().forEach(c -> {
            c.removeProperty(c.getProperty(ProductId.class));
        });
        Ezvcard.write(getVCards())
                .version(VCardVersion.V3_0)
                .prodId(false)
                .go(Path.of(this.output));
    }

    public void removeCategory() throws IOException {
        this.vCards.stream().parallel().forEach(c -> {
            c.removeProperty(c.getProperty(Categories.class));
        });
        Ezvcard.write(getVCards())
                .version(VCardVersion.V3_0)
                .prodId(false)
                .go(Path.of(this.output));
    }


}
