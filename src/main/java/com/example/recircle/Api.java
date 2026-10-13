package com.example.recircle;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

record RegisterInput(@NotBlank String name,@Email @NotBlank String email,@NotBlank String phone,@NotBlank @Size(min=8) String password,@NotBlank String address,@NotBlank String city,@NotBlank String state,@NotBlank String pincode) {}
record LoginInput(@Email @NotBlank String email,@NotBlank String password) {}
record EWasteInput(@NotBlank String category,@NotBlank String itemName,String brand,@NotNull @Min(1) Integer quantity,@NotNull @Positive Double weight,@NotBlank String condition,String description,@NotBlank String pickupAddress) {}
record CollectionInput(@NotNull Long ewasteId,@NotBlank String pickupAddress,@NotNull LocalDate pickupDate,@NotBlank String preferredTime,String notes) {}
record StatusInput(@NotNull CollectionStatus status) {}
record ProfileInput(@NotBlank String name,@NotBlank String phone,@NotBlank String address,@NotBlank String city,@NotBlank String state,@NotBlank String pincode) {}
record LoginOutput(String token, Map<String,Object> user) {}

@Configuration class AppConfig {
    @Bean PasswordEncoder passwordEncoder(){ return new BCryptPasswordEncoder(); }
    @Bean org.springframework.web.servlet.config.annotation.WebMvcConfigurer cors(@Value("${app.cors.origins}") String origins){
        String[] configuredOrigins=Arrays.stream(origins.split(",")).map(String::trim).filter(s->!s.isEmpty()).toArray(String[]::new);
        String[] allowedOrigins=Arrays.copyOf(configuredOrigins,configuredOrigins.length+1);
        allowedOrigins[configuredOrigins.length]="https://e-waste-frontend-self.vercel.app";
        return new org.springframework.web.servlet.config.annotation.WebMvcConfigurer(){
            @Override public void addCorsMappings(org.springframework.web.servlet.config.annotation.CorsRegistry r){r.addMapping("/**").allowedOrigins(allowedOrigins).allowedMethods("GET","POST","PUT","DELETE","OPTIONS").allowedHeaders("*");}
        };
    }
}

@RestController class HealthController {
    @GetMapping("/") Map<String,String> health(){return Map.of("status","ok","service","recircle-api");}
}

@RestControllerAdvice class Errors {
    @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> status(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",Objects.toString(e.getReason(),"Request failed")));}
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class) ResponseEntity<?> conflict(){return ResponseEntity.status(409).body(Map.of("message","A record with that unique value already exists."));}
    @ExceptionHandler(Exception.class) ResponseEntity<?> unexpected(Exception e){return ResponseEntity.badRequest().body(Map.of("message",Objects.toString(e.getMessage(),"Invalid request")));}
}

@RestController @RequestMapping("/auth") class AuthController {
    private final UserRepository users; private final PasswordEncoder encoder; private final Map<String,Long> tokens;
    AuthController(UserRepository users,PasswordEncoder encoder,TokenStore store){this.users=users;this.encoder=encoder;this.tokens=store.tokens;}
    @PostMapping("/register") ResponseEntity<?> register(@Valid @RequestBody RegisterInput in){
        if(users.findByEmailIgnoreCase(in.email()).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Email is already registered.");
        AppUser u=new AppUser(); u.name=in.name().trim();u.email=in.email().trim().toLowerCase();u.phone=in.phone().trim();u.password=encoder.encode(in.password());u.address=in.address().trim();u.city=in.city().trim();u.state=in.state().trim();u.pincode=in.pincode().trim();u.role=Role.USER;users.save(u);
        return ResponseEntity.status(201).body(Map.of("message","Account created. You can now sign in."));
    }
    @PostMapping("/login") LoginOutput login(@Valid @RequestBody LoginInput in){
        AppUser u=users.findByEmailIgnoreCase(in.email()).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Email or password is incorrect."));
        if(!encoder.matches(in.password(),u.password))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Email or password is incorrect.");
        String token=UUID.randomUUID().toString();tokens.put(token,u.id);return new LoginOutput(token,Views.user(u));
    }
    @PostMapping("/logout") Map<String,String> logout(@RequestHeader(value="Authorization",required=false) String h){if(h!=null)tokens.remove(h.replaceFirst("(?i)^Bearer ",""));return Map.of("message","Signed out.");}
}
@Component class TokenStore { final Map<String,Long> tokens=new ConcurrentHashMap<>(); }

class Views {
    static Map<String,Object> user(AppUser u){return Map.of("id",u.id,"name",u.name,"email",u.email,"phone",u.phone,"address",u.address,"city",u.city,"state",u.state,"pincode",u.pincode,"role",u.role.name(),"createdAt",u.createdAt);}
    static Map<String,Object> waste(EWaste w){return Map.ofEntries(Map.entry("id",w.id),Map.entry("userId",w.user.id),Map.entry("ownerName",w.user.name),Map.entry("category",w.category),Map.entry("itemName",w.itemName),Map.entry("brand",Objects.toString(w.brand,"")),Map.entry("quantity",w.quantity),Map.entry("weight",w.weight),Map.entry("condition",w.condition),Map.entry("description",Objects.toString(w.description,"")),Map.entry("pickupAddress",w.pickupAddress),Map.entry("status",w.status),Map.entry("createdAt",w.createdAt));}
    static Map<String,Object> collection(CollectionRequest c){return Map.ofEntries(Map.entry("id",c.id),Map.entry("requestNumber",c.requestNumber),Map.entry("userId",c.user.id),Map.entry("userName",c.user.name),Map.entry("ewasteId",c.ewaste.id),Map.entry("itemName",c.ewaste.itemName),Map.entry("category",c.ewaste.category),Map.entry("pickupAddress",c.pickupAddress),Map.entry("pickupDate",c.pickupDate),Map.entry("preferredTime",c.preferredTime),Map.entry("notes",Objects.toString(c.notes,"")),Map.entry("status",c.status.name()),Map.entry("createdAt",c.createdAt),Map.entry("updatedAt",c.updatedAt));}
}

abstract class SecuredController {
    final UserRepository users; final TokenStore store;
    SecuredController(UserRepository users,TokenStore store){this.users=users;this.store=store;}
    AppUser actor(String header){if(header==null||!header.startsWith("Bearer "))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sign in to continue.");Long id=store.tokens.get(header.substring(7));if(id==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Session expired. Sign in again.");return users.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Account not found."));}
    AppUser admin(String h){AppUser u=actor(h);if(u.role!=Role.ADMIN)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Admin access required.");return u;}
}

@RestController @RequestMapping("/users") class UsersController extends SecuredController {
    UsersController(UserRepository u,TokenStore t){super(u,t);}
    @GetMapping("/me") Map<String,Object> me(@RequestHeader("Authorization") String h){return Views.user(actor(h));}
    @PutMapping("/me") Map<String,Object> update(@RequestHeader("Authorization") String h,@Valid @RequestBody ProfileInput in){AppUser u=actor(h);u.name=in.name();u.phone=in.phone();u.address=in.address();u.city=in.city();u.state=in.state();u.pincode=in.pincode();return Views.user(users.save(u));}
}

@RestController @RequestMapping("/ewaste") class EWasteController extends SecuredController {
    final EWasteRepository repo;
    EWasteController(UserRepository u,TokenStore t,EWasteRepository r){super(u,t);repo=r;}
    @GetMapping List<Map<String,Object>> list(@RequestHeader("Authorization") String h){AppUser a=actor(h);return (a.role==Role.ADMIN?repo.findAll():repo.findByUserIdOrderByCreatedAtDesc(a.id)).stream().map(Views::waste).toList();}
    @PostMapping ResponseEntity<?> create(@RequestHeader("Authorization") String h,@Valid @RequestBody EWasteInput in){EWaste w=new EWaste();w.user=actor(h);apply(w,in);return ResponseEntity.status(201).body(Views.waste(repo.save(w)));}
    @PutMapping("/{id}") Map<String,Object> update(@RequestHeader("Authorization") String h,@PathVariable Long id,@Valid @RequestBody EWasteInput in){AppUser a=actor(h);EWaste w=repo.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Item not found."));if(a.role!=Role.ADMIN&&!w.user.id.equals(a.id))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"You cannot edit this item.");apply(w,in);return Views.waste(repo.save(w));}
    @DeleteMapping("/{id}") ResponseEntity<?> delete(@RequestHeader("Authorization") String h,@PathVariable Long id){AppUser a=actor(h);EWaste w=repo.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Item not found."));if(a.role!=Role.ADMIN&&!w.user.id.equals(a.id))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"You cannot remove this item.");repo.delete(w);return ResponseEntity.noContent().build();}
    private void apply(EWaste w,EWasteInput i){w.category=i.category();w.itemName=i.itemName();w.brand=i.brand();w.quantity=i.quantity();w.weight=i.weight();w.condition=i.condition();w.description=i.description();w.pickupAddress=i.pickupAddress();}
}

@RestController @RequestMapping("/collections") class CollectionController extends SecuredController {
    final CollectionRepository repo; final EWasteRepository waste;
    CollectionController(UserRepository u,TokenStore t,CollectionRepository r,EWasteRepository w){super(u,t);repo=r;waste=w;}
    @GetMapping List<Map<String,Object>> list(@RequestHeader("Authorization") String h){AppUser a=actor(h);return (a.role==Role.ADMIN?repo.findAll():repo.findByUserIdOrderByCreatedAtDesc(a.id)).stream().map(Views::collection).toList();}
    @PostMapping ResponseEntity<?> create(@RequestHeader("Authorization") String h,@Valid @RequestBody CollectionInput in){AppUser a=actor(h);EWaste e=waste.findById(in.ewasteId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"E-waste item not found."));if(!e.user.id.equals(a.id))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Select one of your registered items.");if(in.pickupDate().isBefore(LocalDate.now()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Pickup date must be today or later.");CollectionRequest c=new CollectionRequest();c.user=a;c.ewaste=e;c.requestNumber="RC-"+UUID.randomUUID().toString().substring(0,8).toUpperCase();c.pickupAddress=in.pickupAddress();c.pickupDate=in.pickupDate();c.preferredTime=in.preferredTime();c.notes=in.notes();return ResponseEntity.status(201).body(Views.collection(repo.save(c)));}
    @PutMapping("/{id}") Map<String,Object> update(@RequestHeader("Authorization") String h,@PathVariable Long id,@Valid @RequestBody CollectionInput in){AppUser a=actor(h);CollectionRequest c=repo.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Request not found."));if(!c.user.id.equals(a.id)||c.status!=CollectionStatus.PENDING)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only your pending requests can be edited.");EWaste e=waste.findById(in.ewasteId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"E-waste item not found."));if(!e.user.id.equals(a.id))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Select one of your registered items.");if(in.pickupDate().isBefore(LocalDate.now()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Pickup date must be today or later.");c.ewaste=e;c.pickupAddress=in.pickupAddress();c.pickupDate=in.pickupDate();c.preferredTime=in.preferredTime();c.notes=in.notes();c.updatedAt=LocalDateTime.now();return Views.collection(repo.save(c));}
    @DeleteMapping("/{id}") ResponseEntity<?> delete(@RequestHeader("Authorization") String h,@PathVariable Long id){AppUser a=actor(h);CollectionRequest c=repo.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Request not found."));if(!c.user.id.equals(a.id))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"You cannot remove this request.");c.status=CollectionStatus.CANCELLED;c.updatedAt=LocalDateTime.now();repo.save(c);return ResponseEntity.noContent().build();}
    @PutMapping("/{id}/status") Map<String,Object> status(@RequestHeader("Authorization") String h,@PathVariable Long id,@Valid @RequestBody StatusInput in){admin(h);CollectionRequest c=repo.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Request not found."));c.status=in.status();c.updatedAt=LocalDateTime.now();if(in.status()==CollectionStatus.SCHEDULED||in.status()==CollectionStatus.COLLECTED||in.status()==CollectionStatus.RECYCLED)c.ewaste.status=in.status().name();return Views.collection(repo.save(c));}
}

@RestController @RequestMapping("/admin") class AdminController extends SecuredController {
    final EWasteRepository waste; final CollectionRepository collections;
    AdminController(UserRepository u,TokenStore t,EWasteRepository w,CollectionRepository c){super(u,t);waste=w;collections=c;}
    @GetMapping("/dashboard") Map<String,Object> dashboard(@RequestHeader("Authorization") String h){admin(h);Map<String,Long> statuses=new LinkedHashMap<>();for(CollectionStatus s:CollectionStatus.values())statuses.put(s.name(),collections.countByStatus(s));return Map.of("totalUsers",users.count(),"totalEWaste",waste.count(),"totalCollections",collections.count(),"statuses",statuses);}
    @GetMapping("/users") List<Map<String,Object>> allUsers(@RequestHeader("Authorization") String h){admin(h);return users.findAll().stream().map(Views::user).toList();}
    @PutMapping("/users/{id}") Map<String,Object> role(@RequestHeader("Authorization") String h,@PathVariable Long id,@RequestParam Role role){admin(h);AppUser u=users.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found."));u.role=role;return Views.user(users.save(u));}
    @org.springframework.transaction.annotation.Transactional @DeleteMapping("/users/{id}") ResponseEntity<?> deleteUser(@RequestHeader("Authorization") String h,@PathVariable Long id){admin(h);AppUser u=users.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found."));if(u.role==Role.ADMIN)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Admin accounts cannot be deleted here.");collections.deleteByUserId(id);waste.deleteByUserId(id);users.delete(u);return ResponseEntity.noContent().build();}
    @GetMapping("/ewaste") List<Map<String,Object>> allWaste(@RequestHeader("Authorization") String h){admin(h);return waste.findAll().stream().map(Views::waste).toList();}
    @PutMapping("/ewaste/{id}") Map<String,Object> updateWaste(@RequestHeader("Authorization") String h,@PathVariable Long id,@Valid @RequestBody EWasteInput in){admin(h);EWaste w=waste.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Item not found."));w.category=in.category();w.itemName=in.itemName();w.brand=in.brand();w.quantity=in.quantity();w.weight=in.weight();w.condition=in.condition();w.description=in.description();w.pickupAddress=in.pickupAddress();return Views.waste(waste.save(w));}
    @DeleteMapping("/ewaste/{id}") ResponseEntity<?> deleteWaste(@RequestHeader("Authorization") String h,@PathVariable Long id){admin(h);EWaste w=waste.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Item not found."));if(collections.findAll().stream().anyMatch(c->c.ewaste.id.equals(id)))throw new ResponseStatusException(HttpStatus.CONFLICT,"This item has a collection history and cannot be removed.");waste.delete(w);return ResponseEntity.noContent().build();}
    @GetMapping("/collections") List<Map<String,Object>> allCollections(@RequestHeader("Authorization") String h){admin(h);return collections.findAll().stream().map(Views::collection).toList();}
    @PutMapping("/collections/{id}") Map<String,Object> update(@RequestHeader("Authorization") String h,@PathVariable Long id,@Valid @RequestBody StatusInput in){admin(h);CollectionRequest c=collections.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Request not found."));c.status=in.status();c.updatedAt=LocalDateTime.now();if(in.status()==CollectionStatus.SCHEDULED||in.status()==CollectionStatus.COLLECTED||in.status()==CollectionStatus.RECYCLED)c.ewaste.status=in.status().name();return Views.collection(collections.save(c));}
    @GetMapping("/reports") Map<String,Object> reports(@RequestHeader("Authorization") String h){return dashboard(h);}
}

@Component
class AdminBootstrap implements org.springframework.boot.CommandLineRunner {
    private final UserRepository users;private final PasswordEncoder encoder;@Value("${app.admin.email}") String email;@Value("${app.admin.password}") String password;
    AdminBootstrap(UserRepository u,PasswordEncoder p){users=u;encoder=p;}
    public void run(String... args){if(users.findByEmailIgnoreCase(email).isEmpty()){AppUser a=new AppUser();a.name="ReCircle Administrator";a.email=email.toLowerCase();a.phone="0000000000";a.password=encoder.encode(password);a.address="Operations Centre";a.city="";a.state="";a.pincode="000000";a.role=Role.ADMIN;users.save(a);}}
}
